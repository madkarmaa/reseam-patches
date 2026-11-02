// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "seccomp_open.h"
#include "kernel_stat.h"

#include <fcntl.h>
#include <link.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/ucontext.h>
#include <sys/uio.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <charconv>
#include <climits>
#include <csignal>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <expected>
#include <memory>
#include <mutex>
#include <optional>
#include <ranges>
#include <span>
#include <string>
#include <utility>
#include <vector>

#include "logging.h"
// ReSharper restore CppUnusedIncludeDirective

// Defined in raw_syscall.S. Returns the kernel's result, a negative errno on failure, and leaves
// errno untouched.
extern "C" long signature_raw_syscall(long number, long first, long second, long third,
                                      long fourth, long fifth, long sixth);

namespace {
#ifdef __aarch64__
    constexpr uint32_t syscall_arch = AUDIT_ARCH_AARCH64;
#elifdef __arm__
    constexpr uint32_t syscall_arch = AUDIT_ARCH_ARM;
#else
#error Unsupported raw syscall architecture
#endif

    // Passed in the unused fifth argument of intercepted syscalls, so the filter lets the
    // handler's own replayed calls through.
    constexpr uint32_t replay_marker = 0x534b4150;
    // Reported in si_errno, so the handler recognizes traps raised by this filter.
    constexpr uint16_t trap_tag = 0x534b;
    // Limits redirected and preserved syscall events separately so diagnostics stay bounded.
    constexpr unsigned reported_event_limit = 256;

    // dl_iterate_phdr fixes the callback signature for every module query.
    using dl_callback_t = int (*)(dl_phdr_info *, size_t, void *);

    struct descriptor_identity {
        std::string resolved_replacement;
        struct stat installed{};
        struct stat replacement{};
        int original_descriptor = -1;
    };

    // The filter indexes descriptors by number, so the table must stay a runtime-sized array.
    class descriptor_table {
    public:
        void reset(const size_t count) {
            entries.reset(new std::atomic<int>[count]);
        }

        [[nodiscard]] std::atomic<int> &slot(const size_t descriptor) const {
            return *(entries.get() + descriptor);
        }

    private:
        struct table_deleter {
            void operator()(const std::atomic<int> *const descriptors) const { delete[] descriptors; }
        };

        std::unique_ptr<std::atomic<int>, table_deleter> entries;
    };

    struct executable_range {
        uintptr_t start;
        uintptr_t end;
        const executable_range *next;
    };

    struct redirect_state {
        std::string library;
        // The span of the library's executable segments.
        uintptr_t start = UINTPTR_MAX;
        uintptr_t end = 0;
        // Published once per unpacked mapping. Readers never allocate or take a lock.
        std::atomic<const executable_range *> unpacked_ranges{nullptr};
        std::atomic<unsigned> filters{0};
        // Loaded libraries with that basename. Exactly one is required.
        unsigned libraries_found = 0;
        // Set once the filter is in place. An installed state is never replaced.
        bool installed = false;
        std::vector<raw_open_mapping> paths;
        bool preserve_descriptor_identity = false;
        bool redirect_file_contents = false;
        int (*snapshot_proc_file)(proc_file, int) = nullptr;
        bool (*bypass_current_thread)() = nullptr;
        int installing_process = 0;
        size_t descriptor_count = 0;
        descriptor_table proc_descriptors;
        std::vector<descriptor_identity> identities;
        // Receives every SIGSYS this filter did not raise.
        struct sigaction previous_handler{};
        // The write end of the logger thread's pipe, or -1 when diagnostics are off.
        std::atomic<int> event_pipe{-1};
        std::atomic<unsigned> redirected_events{0};
        std::atomic<unsigned> preserved_events{0};
    };

    constinit std::mutex install_mutex;
    // Published before the signal handler is installed, and retained until process exit.
    constinit std::atomic<redirect_state *> active{nullptr};
    static_assert(decltype(active)::is_always_lock_free);
    static_assert(std::atomic<unsigned>::is_always_lock_free);
    static_assert(std::atomic<int>::is_always_lock_free);

    // Every index below was validated against its container's size, and at() could throw on the
    // signal stack, so iteration stays unchecked.
    template<typename Values>
    auto &element_at(Values &values, const size_t index) {
        auto position = values.begin() + static_cast<std::ptrdiff_t>(index);
        return *position;
    }

    // Records the executable segments of each loaded library with the configured name.
    // The loader requires a mutable module pointer in its callback signature.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    const dl_callback_t find_library = [](dl_phdr_info *const info, size_t /*info_size*/,
                                          void *const data) {
        auto &state = *static_cast<redirect_state *>(data);

        const char *const filename = info->dlpi_name == nullptr ? "" : info->dlpi_name;
        const char *const slash = strrchr(filename, '/');

        if (const char *const basename = slash == nullptr ? filename : slash + 1;
            state.library != basename)
            return 0;

        state.libraries_found++;
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;

            const uintptr_t start = info->dlpi_addr + segment.p_vaddr;
            state.start = std::min(state.start, start);
            state.end = std::max(state.end, start + static_cast<uintptr_t>(segment.p_memsz));
        }

        return 0;
    };

    // Reading through a syscall only needs the address; the kernel reads through these words.
    // process_vm_readv and process_vm_writev take this layout by value in registers only, so a
    // read-only mirror of iovec avoids discarding constness for buffers the kernel never writes.
    struct readable_iovec {
        const void *base;
        std::size_t length;
    };
    static_assert(sizeof(readable_iovec) == sizeof(iovec));
    static_assert(alignof(readable_iovec) == alignof(iovec));
    static_assert(offsetof(readable_iovec, base) == offsetof(iovec, iov_base));
    static_assert(offsetof(readable_iovec, length) == offsetof(iovec, iov_len));

    // The kernel copies the path, so an invalid pointer returns EFAULT instead of faulting in the
    // signal handler. A partial copy is enough when it includes a matching path's terminating NUL.
    long read_path(const char *const path, const std::span<char> buffer) {
        iovec local{.iov_base = buffer.data(), .iov_len = buffer.size()};
        const readable_iovec remote{.base = path, .length = buffer.size()};

        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_readv, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    // The handler relies on read_path, which a sandbox may forbid.
    bool path_copying_available(const char *const sample) {
        char copied = 0;
        if (read_path(sample, std::span(&copied, 1)) != 1) return false;
        return copied == sample[0];
    }

    bool prepare_descriptor_identities(redirect_state &state) {
        // Select libc's variadic overload explicitly for these fixed read-only flags.
        int (*const open_file)(const char *, int, ...) = open;
        for (const auto &[installed_path, replacement_path] : state.paths) {
            descriptor_identity identity;
            const int installed = open_file(installed_path, O_RDONLY | O_CLOEXEC, 0);
            if (installed < 0) return false;

            const bool inspected = fstat(installed, &identity.installed) == 0;
            close(installed);
            if (!inspected) return false;

            const int replacement = open_file(replacement_path, O_RDONLY | O_CLOEXEC, 0);
            if (replacement < 0) return false;
            const bool replacement_inspected = fstat(replacement, &identity.replacement) == 0;

            std::array<char, 64> descriptor{};
            const int formatted = snprintf(descriptor.data(), descriptor.size(),
                                           "/proc/self/fd/%d", replacement);

            std::array<char, PATH_MAX> resolved{};

            const ssize_t length = formatted > 0 && std::cmp_less(formatted, descriptor.size())
                    ? readlink(descriptor.data(), resolved.data(), resolved.size() - 1) : -1;

            if (state.redirect_file_contents) identity.original_descriptor = replacement;
            else close(replacement);

            if (!replacement_inspected || length <= 0) return false;

            identity.resolved_replacement.assign(resolved.data(), length);
            state.identities.push_back(std::move(identity));
        }

        return true;
    }

    struct syscall_event {
        int syscall;
        long result;
        // A negative index means the original path was preserved.
        int mapping;
    };

    // Names a trapped syscall for the log, which runs on the logger thread.
    const char *syscall_name(const int number) {
        if (number == __NR_readlinkat) return "readlinkat";
        if (number == __NR_read) return "read";
        if (number == __NR_pread64) return "pread64";
        if (number == __NR_close) return "close";
#ifdef __NR_mmap
        if (number == __NR_mmap) return "mmap";
#endif
#ifdef __NR_mmap2
        if (number == __NR_mmap2) return "mmap2";
#endif
#ifdef __NR_lseek
        if (number == __NR_lseek) return "lseek";
#endif
#ifdef __NR__llseek
        if (number == __NR__llseek) return "_llseek";
#endif
        if (number == __NR_fstat) return "fstat";
#ifdef __NR_open
        if (number == __NR_open) return "open";
#endif
#ifdef __NR_readlink
        if (number == __NR_readlink) return "readlink";
#endif
#ifdef __NR_fstat64
        if (number == __NR_fstat64) return "fstat64";
#endif
#ifdef __NR_newfstatat
        if (number == __NR_newfstatat) return "newfstatat";
#endif
#ifdef __NR_fstatat64
        if (number == __NR_fstatat64) return "fstatat64";
#endif
        return "openat";
    }

    // Logs the events the signal handler writes to the pipe, since the handler itself cannot log.
    void log_syscall_events(const int descriptor) {
        syscall_event event{};
        while (true) {
            const ssize_t length = read(descriptor, &event, sizeof(event));
            if (length < 0 && errno == EINTR) continue;
            if (length != sizeof(event)) break;

            LOG_INFO("Raw %s %s: mapping %d, kernel result %ld", syscall_name(event.syscall),
                     event.mapping >= 0 ? "redirected" : "preserved", event.mapping, event.result);
        }

        close(descriptor);
    }

    // Without a logger thread the handler reports nothing.
    void start_event_logger(redirect_state &state) {
        std::array<int, 2> descriptors{};
        if (pipe2(descriptors.data(), O_CLOEXEC) != 0) return;

        // A full pipe must drop events rather than block the trapped thread.
        pthread_t logger{};
        const auto [read_pipe, write_pipe] = descriptors;
        if (fcntl(write_pipe, F_SETFL, O_NONBLOCK) == 0 &&
            pthread_create(&logger, nullptr, [](void *const descriptor_argument) -> void * {
                log_syscall_events(static_cast<int>(reinterpret_cast<intptr_t>(descriptor_argument)));
                return nullptr;
            }, reinterpret_cast<void *>(static_cast<intptr_t>(read_pipe))) == 0) {

            pthread_detach(logger);

            state.event_pipe.store(write_pipe, std::memory_order_relaxed);

            return;
        }

        close(read_pipe);
        close(write_pipe);
    }

    // Everything from here to handle_sigsys runs on the signal stack. Every operation there is a
    // lock-free atomic, a bounded memory access, or a direct syscall. In particular, logging,
    // allocation, JNI and the libc file hooks never run on this stack.

    bool is_open_syscall(const int number) {
#ifdef __NR_open
        return number == __NR_openat || number == __NR_open;
#else
        return number == __NR_openat;
#endif
    }

    bool is_readlink_syscall(const int number) {
#ifdef __NR_readlink
        return number == __NR_readlinkat || number == __NR_readlink;
#else
        return number == __NR_readlinkat;
#endif
    }

    bool is_fstat_syscall(const int number) {
#ifdef __NR_fstat64
        return number == __NR_fstat || number == __NR_fstat64;
#else
        return number == __NR_fstat;
#endif
    }

    bool is_fstatat_syscall(const int number) {
        constexpr auto syscalls = std::to_array({
#ifdef __NR_newfstatat
            __NR_newfstatat,
#endif
#ifdef __NR_fstatat64
            __NR_fstatat64,
#endif
        });
        return std::ranges::contains(syscalls, number);
    }

    bool is_mmap_syscall(const int number) {
#ifdef __aarch64__
        return number == __NR_mmap;
#else
        return number == __NR_mmap2;
#endif
    }

    bool contains_address(const redirect_state &state, const uintptr_t address) {
        if (address >= state.start && address <= state.end) return true;

        for (const executable_range *range = state.unpacked_ranges.load(std::memory_order_acquire);
             range != nullptr; range = range->next)
            if (address >= range->start && address <= range->end) return true;

        return false;
    }

    bool supports_syscall(const redirect_state &state, const int number) {
        if (is_open_syscall(number)) return true;

        if (state.redirect_file_contents &&
            (number == __NR_read || number == __NR_pread64 || number == __NR_close ||
             is_mmap_syscall(number)
#ifdef __NR_lseek
             || number == __NR_lseek
#endif
#ifdef __NR__llseek
             || number == __NR__llseek
#endif
            ))
            return true;

        return state.preserve_descriptor_identity &&
                (is_readlink_syscall(number) || is_fstat_syscall(number) || is_fstatat_syscall(number));
    }

    bool is_redirect_trap(const redirect_state &state, const siginfo_t &info) {
        const auto caller = reinterpret_cast<uintptr_t>(info.si_call_addr);
        const bool opens_file = is_open_syscall(info.si_syscall);

        return info.si_code == SYS_SECCOMP && std::cmp_equal(info.si_errno, trap_tag) &&
               supports_syscall(state, info.si_syscall) && info.si_arch == syscall_arch &&
               (!(opens_file || state.redirect_file_contents) ||
                contains_address(state, caller));
    }

    // Gives a SIGSYS this filter did not raise to the disposition installed before it.
    void forward_to_previous_handler(const struct sigaction &previous, const int signal,
                                     siginfo_t *const info, void *const context) {
        if (previous.sa_handler == SIG_IGN) return;

        if (previous.sa_handler == SIG_DFL) {
            // Raise again under the default action, which runs once this handler returns. Without
            // that action, raising would only re-enter this handler.
            if (sigaction(signal, &previous, nullptr) != 0) return;
            static_cast<void>(raise(signal));
            return;
        }

        if ((static_cast<unsigned>(previous.sa_flags) & SA_SIGINFO) != 0)
            previous.sa_sigaction(signal, info, context);
        else
            previous.sa_handler(signal);
    }

    // Raw register values; each syscall handler assigns their syscall-specific meaning.
    struct syscall_arguments {
        long first;
        long second;
        long third;
        long fourth;
        long fifth;
        long sixth;
    };

    syscall_arguments read_syscall_arguments(const mcontext_t &registers) {
#ifdef __aarch64__
        return {
            .first = static_cast<long>(registers.regs[0]),
            .second = static_cast<long>(registers.regs[1]),
            .third = static_cast<long>(registers.regs[2]),
            .fourth = static_cast<long>(registers.regs[3]),
            .fifth = static_cast<long>(registers.regs[4]),
            .sixth = static_cast<long>(registers.regs[5]),
        };
#elifdef __arm__
        return {
            .first = static_cast<long>(registers.arm_r0),
            .second = static_cast<long>(registers.arm_r1),
            .third = static_cast<long>(registers.arm_r2),
            .fourth = static_cast<long>(registers.arm_r3),
            .fifth = static_cast<long>(registers.arm_r4),
            .sixth = static_cast<long>(registers.arm_r5),
        };
#endif
    }

    void set_syscall_result(mcontext_t &registers, const long result) {
#ifdef __aarch64__
        registers.regs[0] = result;
#elifdef __arm__
        registers.arm_r0 = result;
#endif
    }

    // Volatile reads keep the compiler from replacing this loop with an out-of-line strcmp.
    bool copied_path_equals(const volatile char *const copied, const size_t length,
                            const char *const expected) {
        for (size_t offset = 0; offset < length && copied[offset] == expected[offset]; offset++) {
            if (copied[offset] == '\0') return true;
        }
        return false;
    }

    // Returns the index of the mapping for the copied path, or -1 when nothing matches.
    int find_mapping(const redirect_state &state, const char *const copied, const long length) {
        if (length <= 0) return -1;

        const auto copied_length = static_cast<size_t>(length);
        const auto mapping = std::ranges::find_if(state.paths, [&](const raw_open_mapping &candidate) {
            return copied_path_equals(copied, copied_length, candidate.installed);
        });

        return mapping == state.paths.end() ? -1
                : static_cast<int>(std::distance(state.paths.begin(), mapping));
    }

    // Hands the event to the logger thread. A full pipe drops it.
    void report_event(redirect_state &state, const int syscall, const long result, const int mapping) {
        const int event_pipe = state.event_pipe.load(std::memory_order_relaxed);
        if (event_pipe < 0) return;

        if (auto &counter = mapping >= 0 ? state.redirected_events : state.preserved_events;
            counter.fetch_add(1, std::memory_order_relaxed) >= reported_event_limit)
            return;

        syscall_event event{.syscall = syscall, .result = result, .mapping = mapping};
        signature_raw_syscall(__NR_write, event_pipe, reinterpret_cast<long>(&event),
                              sizeof(event), 0, 0, 0);
    }

    long write_memory(const std::span<char> destination, const std::span<const char> source) {
        const readable_iovec local{.base = source.data(), .length = source.size()};
        iovec remote{.iov_base = destination.data(), .iov_len = destination.size()};

        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_writev, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    int find_replacement_mapping(const redirect_state &state, const char *const path,
                                  const size_t length) {
        const auto identity = std::ranges::find_if(state.identities,
                [&](const descriptor_identity &candidate) {
                    return copied_path_equals(path, length, candidate.resolved_replacement.c_str());
                });

        return identity == state.identities.end() ? -1
                : static_cast<int>(std::distance(state.identities.begin(), identity));
    }

    int find_descriptor_mapping(const redirect_state &state, const long descriptor) {
        if (descriptor < 0) return -1;

        constexpr size_t descriptor_prefix_length = sizeof("/proc/self/fd/") - 1;
        std::array<char, 64> path{};
        constexpr auto descriptor_prefix = std::to_array("/proc/self/fd/");

        memcpy(path.data(), descriptor_prefix.data(), descriptor_prefix_length);

        const auto [number_end, number_error] = std::to_chars(
                path.data() + descriptor_prefix_length, path.data() + path.size() - 1, descriptor);

        if (number_error != std::errc{}) return -1;

        *number_end = '\0';
        // readlinkat fills the used prefix. Avoid zeroing PATH_MAX bytes on the signal stack.
        std::array<char, PATH_MAX> resolved;
        const long length = signature_raw_syscall(
                __NR_readlinkat, AT_FDCWD, reinterpret_cast<long>(path.data()),
                reinterpret_cast<long>(resolved.data()), resolved.size() - 1, replay_marker, 0);

        if (length <= 0) return -1;

        // The syscall's capacity reserves this terminator inside the array.
        element_at(resolved, static_cast<size_t>(length)) = '\0';

        return state.redirect_file_contents ? find_mapping(state, resolved.data(), length + 1)
                : find_replacement_mapping(state, resolved.data(), length + 1);
    }

    long redirect_readlink(const redirect_state &state, const int number,
                           const syscall_arguments &arguments, int &mapping) {
        const bool has_directory = number == __NR_readlinkat;

        const auto *const path = reinterpret_cast<const char *>(
                has_directory ? arguments.second : arguments.first);

        auto *const buffer = reinterpret_cast<char *>(has_directory ? arguments.third : arguments.second);
        const auto size = static_cast<size_t>(has_directory ? arguments.fourth : arguments.third);

        const auto replay = [&](char *const destination, const size_t capacity) {
            return has_directory ? signature_raw_syscall(
                    __NR_readlinkat, arguments.first, reinterpret_cast<long>(path),
                    reinterpret_cast<long>(destination), static_cast<long>(capacity), replay_marker, 0)
                    : signature_raw_syscall(number, reinterpret_cast<long>(path),
                                            reinterpret_cast<long>(destination), static_cast<long>(capacity),
                                            0, replay_marker, 0);
        };

        const long result = replay(buffer, size);
        if (result <= 0 || result >= PATH_MAX) return result;

        // The kernel or read_path fills the used prefix, as in find_descriptor_mapping.
        std::array<char, PATH_MAX> resolved;
        long resolved_length = result;

        // A short caller buffer may contain only a prefix of the replacement path. Resolve the
        // full target for matching, while keeping the original result for unrelated descriptors.
        if (std::cmp_equal(result, size)) {
            resolved_length = replay(resolved.data(), resolved.size() - 1);
            if (resolved_length <= 0 || resolved_length >= PATH_MAX) return result;
        } else if (read_path(buffer, std::span(resolved).first(static_cast<size_t>(result))) != result) {
            return -EFAULT;
        }

        // Both reads are bounded above by PATH_MAX - 1.
        element_at(resolved, static_cast<size_t>(resolved_length)) = '\0';

        mapping = find_replacement_mapping(state, resolved.data(), resolved_length + 1);
        if (mapping < 0) return result;

        // find_replacement_mapping returns an index into the parallel path and identity tables.
        const char *const installed = element_at(state.paths, static_cast<size_t>(mapping)).installed;
        const size_t length = std::min(size, strlen(installed));

        if (write_memory(std::span(buffer, length), std::span(installed, length)) !=
            static_cast<long>(length))
            return -EFAULT;

        return static_cast<long>(length);
    }

    template<typename Metadata>
    long redirect_fstat(const redirect_state &state, const int number, const long descriptor,
                        void *const buffer, int &mapping) {
        mapping = find_descriptor_mapping(state, descriptor);
        if (mapping < 0)
            return signature_raw_syscall(number, descriptor,
                                          reinterpret_cast<long>(buffer), 0, 0, replay_marker, 0);

        Metadata value{};
        const long result = signature_raw_syscall(number, descriptor,
                                                  reinterpret_cast<long>(&value), 0, 0, replay_marker, 0);
        if (result < 0) return result;

        // find_descriptor_mapping returns an index into identities, or -1 handled above.
        const auto &identity = element_at(state.identities, static_cast<size_t>(mapping));

        // Preserve the real inode and content metadata, which must agree with memory mappings.
        // Only ownership distinguishes this copy from an installed, system-owned APK.
        value.st_uid = static_cast<decltype(value.st_uid)>(identity.installed.st_uid);
        value.st_gid = static_cast<decltype(value.st_gid)>(identity.installed.st_gid);

        if (state.redirect_file_contents)
            value.st_size = identity.replacement.st_size;

        if (write_memory(std::span(static_cast<char *>(buffer), sizeof(value)),
                         std::span(reinterpret_cast<const char *>(&value), sizeof(value))) !=
            static_cast<long>(sizeof(value)))
            return -EFAULT;

        return 0;
    }

    int find_stat_path_mapping(const redirect_state &state, const long directory,
                               const char *const path, const long length) {
        if (length <= 0 || path[0] == '\0') return -1;
        if (path[0] == '/') return find_mapping(state, path, length);

        std::array<char, 64> descriptor_path{};
        constexpr auto prefix = std::to_array("/proc/self/fd/");

        memcpy(descriptor_path.data(), prefix.data(), prefix.size() - 1);
        const auto [number_end, number_error] = std::to_chars(
                descriptor_path.data() + prefix.size() - 1,
                descriptor_path.data() + descriptor_path.size() - 1, directory);

        if (number_error != std::errc{}) return -1;

        *number_end = '\0';

        // Resolve the directory only; the kernel still handles the caller's flags and errors.
        std::array<char, PATH_MAX> absolute;
        const long prefix_length = signature_raw_syscall(
                __NR_readlinkat, AT_FDCWD,
                reinterpret_cast<long>(directory == AT_FDCWD ? "/proc/self/cwd" : descriptor_path.data()),
                reinterpret_cast<long>(absolute.data()), absolute.size() - 1, replay_marker, 0);

        if (prefix_length <= 0) return -1;

        auto offset = static_cast<size_t>(prefix_length);

        if (element_at(absolute, offset - 1) != '/') element_at(absolute, offset++) = '/';

        for (size_t index = 0; std::cmp_less(index, length) && offset < absolute.size(); index++) {
            element_at(absolute, offset++) = path[index];

            if (path[index] == '\0')
                return find_mapping(state, absolute.data(), static_cast<long>(offset));
        }

        return -1;
    }

    template<typename Metadata>
    
    long redirect_fstatat(const redirect_state &state, const int number,
                          const syscall_arguments &arguments, int &mapping) {
        const auto *const path = reinterpret_cast<const char *>(arguments.second);
        Metadata value{};

        long result = signature_raw_syscall(number, arguments.first, arguments.second,
                                            reinterpret_cast<long>(&value), arguments.fourth,
                                            replay_marker, 0);

        if (result < 0) return result;

        // A no-follow query describes the symlink itself, including procfs descriptor links.
        if (!S_ISLNK(value.st_mode)) {
            std::array<char, PATH_MAX> copied;

            const long length = read_path(path, std::span(copied));

            mapping = find_stat_path_mapping(state, arguments.first, copied.data(), length);
            if (mapping >= 0 && !state.redirect_file_contents) {
                // The mapping index was validated against the path table.
                result = signature_raw_syscall(number, AT_FDCWD,
                        reinterpret_cast<long>(element_at(state.paths, static_cast<size_t>(mapping)).replacement),
                        reinterpret_cast<long>(&value), arguments.fourth, replay_marker, 0);

                if (result < 0) return result;
            } else {
                // AT_EMPTY_PATH, proc-fd paths and other aliases already resolve the real copy.
                for (const auto &identity : state.identities) {
                    const auto &replacement = state.redirect_file_contents
                            ? identity.installed : identity.replacement;

                    if (value.st_dev != replacement.st_dev || value.st_ino != replacement.st_ino)
                        continue;

                    mapping = static_cast<int>(&identity - state.identities.data());
                    break;
                }
            }
        }

        if (mapping >= 0) {
            // Keep inode, size and timestamps consistent with the replacement descriptor.
            const auto &identity = element_at(state.identities, static_cast<size_t>(mapping));

            value.st_uid = static_cast<decltype(value.st_uid)>(identity.installed.st_uid);
            value.st_gid = static_cast<decltype(value.st_gid)>(identity.installed.st_gid);

            if (state.redirect_file_contents)
                value.st_size = identity.replacement.st_size;
        }
        
        if (write_memory(std::span(reinterpret_cast<char *>(arguments.third), sizeof(value)),
                         std::span(reinterpret_cast<const char *>(&value), sizeof(value))) !=
            static_cast<long>(sizeof(value)))
            return -EFAULT;
        
        return 0;
    }

    long read_original(const int descriptor, const long buffer, const long count, const int64_t offset) {
#ifdef __arm__
        return signature_raw_syscall(__NR_pread64, descriptor, buffer, count, 0,
                                     static_cast<long>(static_cast<uint32_t>(offset)),
                                     static_cast<long>(static_cast<uint32_t>(static_cast<uint64_t>(offset) >> 32U)));
#else
        return signature_raw_syscall(__NR_pread64, descriptor, buffer, count, offset, 0, 0);
#endif
    }

    std::expected<int64_t, long> current_position(const long descriptor) {
#ifdef __arm__
        int64_t position = 0;
        const long result = signature_raw_syscall(__NR__llseek, descriptor, 0, 0,
                                                   reinterpret_cast<long>(&position), SEEK_CUR, 0);
        if (result < 0) return std::unexpected(result);
        return position;
#else
        const long position = signature_raw_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0);
        if (position < 0) return std::unexpected(position);
        return position;
#endif
    }

    void advance_position(const long descriptor, const long count) {
#ifdef __arm__
        int64_t position = 0;
        static_cast<void>(signature_raw_syscall(__NR__llseek, descriptor, 0, count,
                                                reinterpret_cast<long>(&position), SEEK_CUR, 0));
#else
        static_cast<void>(signature_raw_syscall(__NR_lseek, descriptor, count, SEEK_CUR, 0, 0, 0));
#endif
    }

    // Reads the original bytes behind a replaced descriptor, at the same absolute offset.
    std::optional<long> read_replaced_bytes(const redirect_state &state, const int number,
                                            const syscall_arguments &arguments) {
        const long descriptor = arguments.first;

        if (descriptor < 0 || !std::cmp_less(descriptor, state.descriptor_count)) return std::nullopt;

        const auto slot = static_cast<size_t>(descriptor);
        const int snapshot = number == __NR_close
                ? state.proc_descriptors.slot(slot).exchange(-1, std::memory_order_acq_rel)
                : state.proc_descriptors.slot(slot).load(std::memory_order_acquire);

        if (snapshot >= 0 && number == __NR_close)
            signature_raw_syscall(__NR_close, snapshot, 0, 0, 0, 0, 0);

        if (snapshot >= 0 && number == __NR_pread64)
            return signature_raw_syscall(number, snapshot, arguments.second, arguments.third,
                                         arguments.fourth, arguments.fifth, arguments.sixth);

        if (snapshot >= 0 && number == __NR_read) {
            const auto position = current_position(descriptor);
            if (!position) return position.error();

            const long result = read_original(snapshot, arguments.second, arguments.third, *position);

            if (result > 0) advance_position(descriptor, result);

            return result;
        }

        return std::nullopt;
    }

    long redirect_content_io(const redirect_state &state, const int number,
                             const syscall_arguments &arguments, int &mapping) {
        if (is_mmap_syscall(number)) {
            // Anonymous mappings ignore the descriptor. Only file mappings receive original bytes.
            if (const auto flags = static_cast<unsigned long>(arguments.fourth);
                (flags & static_cast<unsigned long>(MAP_ANONYMOUS)) == 0)
                mapping = find_descriptor_mapping(state, arguments.fifth);

            const long descriptor = mapping < 0 ? arguments.fifth
                    : element_at(state.identities, static_cast<size_t>(mapping)).original_descriptor;

            return signature_raw_syscall(number, arguments.first, arguments.second, arguments.third,
                                         arguments.fourth, descriptor, arguments.sixth);
        }

        if (const std::optional<long> handled = read_replaced_bytes(state, number, arguments);
            handled.has_value())
            return handled.value();

        const long descriptor = arguments.first;

        // Resolve each use so descriptors opened before installation, aliases and dup() work,
        // and a close/reuse outside the protected library cannot leave a stale mapping.
        mapping = find_descriptor_mapping(state, descriptor);
        if (mapping < 0 || number == __NR_close)
            return signature_raw_syscall(number, arguments.first, arguments.second, arguments.third,
                                         arguments.fourth, arguments.fifth, arguments.sixth);

        const auto &identity = element_at(state.identities, static_cast<size_t>(mapping));

        if (number == __NR_pread64)
            return signature_raw_syscall(number, identity.original_descriptor,
                                         arguments.second, arguments.third, arguments.fourth,
                                         arguments.fifth, arguments.sixth);
#ifdef __NR__llseek
        if (number == __NR__llseek) {
            if (arguments.fifth != SEEK_END)
                return signature_raw_syscall(number, descriptor, arguments.second, arguments.third,
                                             arguments.fourth, arguments.fifth, 0);

            const uint64_t offset = (static_cast<uint64_t>(static_cast<uint32_t>(arguments.second)) << 32U) |
                    static_cast<uint32_t>(arguments.third);
            const uint64_t position = static_cast<uint64_t>(identity.replacement.st_size) + offset;
            return signature_raw_syscall(number, descriptor,
                                         static_cast<long>(static_cast<uint32_t>(position >> 32U)),
                                         static_cast<long>(static_cast<uint32_t>(position)),
                                         arguments.fourth, SEEK_SET, 0);
        }
#endif
#ifdef __NR_lseek
        if (number == __NR_lseek) {
            if (arguments.third != SEEK_END)
                return signature_raw_syscall(number, descriptor, arguments.second, arguments.third, 0, 0, 0);

            return signature_raw_syscall(number, descriptor,
                                         static_cast<long>(identity.replacement.st_size + arguments.second),
                                         SEEK_SET, 0, 0, 0);
        }

        const auto position = current_position(descriptor);
        if (!position) return position.error();

        const long result = read_original(identity.original_descriptor,
                                          arguments.second, arguments.third, *position);

        if (result > 0) advance_position(descriptor, result);

        return result;
#else
        return -ENOSYS;
#endif
    }

    // Replays a descriptor metadata query through the identity table.
    long redirect_metadata(const redirect_state &state, const int number,
                           const syscall_arguments &arguments, int &mapping) {
        if (is_readlink_syscall(number))
            return redirect_readlink(state, number, arguments, mapping);
#ifdef __NR_newfstatat
        if (number == __NR_newfstatat)
            return redirect_fstatat<signature_kernel_stat>(state, number, arguments, mapping);
#endif
#ifdef __NR_fstatat64
        if (number == __NR_fstatat64)
            return redirect_fstatat<signature_kernel_stat64>(state, number, arguments, mapping);
#endif
#ifdef __NR_fstat64
        if (number == __NR_fstat64)
            return redirect_fstat<signature_kernel_stat64>(
                    state, number, arguments.first,
                    reinterpret_cast<void *>(arguments.second), mapping);
#endif
        return redirect_fstat<signature_kernel_stat>(
                state, number, arguments.first,
                reinterpret_cast<void *>(arguments.second), mapping);
    }

    // Replays a trapped open against the mapped replacement path.
    long redirect_open(const redirect_state &state, const int number,
                       const syscall_arguments &arguments, int &mapping) {
        const bool has_directory = number == __NR_openat;
        const auto *const requested_path = reinterpret_cast<const char *>(
                has_directory ? arguments.second : arguments.first);

        // Zeroing would cost a full PATH_MAX write per trapped open; read_path fills what is used.
        std::array<char, PATH_MAX> copied;
        const long length = read_path(requested_path, std::span(copied));
        mapping = find_mapping(state, copied.data(), length);

        // find_mapping returns a validated path-table index, or -1 for an unmatched path.
        const char *const path = mapping >= 0 && !state.redirect_file_contents
                ? element_at(state.paths, static_cast<size_t>(mapping)).replacement : requested_path;

        const long result = has_directory ? signature_raw_syscall(__NR_openat, arguments.first,
                                                  reinterpret_cast<long>(path), arguments.third,
                                                  arguments.fourth,
                                                  replay_marker, 0)
                : signature_raw_syscall(number, reinterpret_cast<long>(path), arguments.second,
                                        arguments.third, 0, replay_marker, 0);

        if (result < 0 || !std::cmp_less(result, state.descriptor_count))
            return result;

        const auto slot = static_cast<size_t>(result);
        if (const auto previous = state.proc_descriptors.slot(slot).exchange(-1, std::memory_order_acq_rel);
            previous >= 0)
            signature_raw_syscall(__NR_close, previous, 0, 0, 0, 0, 0);

        const auto copied_size = length > 0 ? static_cast<size_t>(length) : 0;

        if (state.snapshot_proc_file == nullptr || copied_size == 0) return result;

        std::string_view proc_path(copied.data(), copied_size);
        const auto terminator = proc_path.find('\0');
        if (terminator == std::string_view::npos) return result;
        proc_path = proc_path.substr(0, terminator);
        if (!proc_path.starts_with("/proc/")) return result;
        proc_path.remove_prefix(6);
        const auto separator = proc_path.find('/');
        if (separator == std::string_view::npos) return result;
        const auto identity = proc_path.substr(0, separator);
        const auto file = proc_path.substr(separator + 1);
        if (file != "maps" && file != "status") return result;

        const auto current_process = static_cast<int>(signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0));
        int process = current_process;
        if (identity != "self") {
            if (const auto [parsed_end, parse_error] = std::from_chars(identity.data(), identity.data() + identity.size(), process);
                parse_error != std::errc{} || parsed_end != identity.data() + identity.size() ||
                (process != current_process && process != state.installing_process))
                return result;
        }

        const int snapshot = state.snapshot_proc_file(file == "maps" ? proc_file::maps : proc_file::status, process);

        if (snapshot >= 0) {
            state.proc_descriptors.slot(slot).store(snapshot, std::memory_order_release);
            return result;
        }

        signature_raw_syscall(__NR_close, result, 0, 0, 0, 0, 0);
        return snapshot;
    }

    long redirect_file_syscall(redirect_state &state, const int number,
                              const syscall_arguments &arguments) {
        int mapping = -1;
        long result = 0;
        if (state.redirect_file_contents && !is_open_syscall(number) &&
            !is_readlink_syscall(number) && !is_fstat_syscall(number) && !is_fstatat_syscall(number))
            result = redirect_content_io(state, number, arguments, mapping);
        else if (state.preserve_descriptor_identity && !is_open_syscall(number))
            result = redirect_metadata(state, number, arguments, mapping);
        else
            result = redirect_open(state, number, arguments, mapping);

        report_event(state, number, result, mapping);
        return result;
    }

    // Replays intercepted file syscalls, changing only mapped paths or their descriptor metadata.
    void handle_sigsys(const int signal, siginfo_t *const info, void *const context) {
        redirect_state *const state = active.load(std::memory_order_acquire);
        if (!is_redirect_trap(*state, *info)) {
            forward_to_previous_handler(state->previous_handler, signal, info, context);
            return;
        }

        mcontext_t &registers = static_cast<ucontext_t *>(context)->uc_mcontext;
        const auto arguments = read_syscall_arguments(registers);
        if (state->bypass_current_thread != nullptr && state->bypass_current_thread()) {
            const long fifth = state->redirect_file_contents ? arguments.fifth : replay_marker;
            set_syscall_result(registers, signature_raw_syscall(info->si_syscall,
                    arguments.first, arguments.second, arguments.third, arguments.fourth,
                    fifth, arguments.sixth));
            return;
        }
        set_syscall_result(registers,
                           redirect_file_syscall(*state, info->si_syscall, arguments));
    }

    struct filter_failure {
        const char *cause;
        long value;
    };

    // Fails with an errno, or with the ID of a thread that could not synchronize.
    std::expected<void, filter_failure> install_filter(const uintptr_t start, const uintptr_t end,
                                                       const bool preserve_identity,
                                                       const bool redirect_contents) {
        const uint64_t first = start;
        const uint64_t last = end;
#ifdef __NR_open
        constexpr uint8_t legacy_open_count = 1;
#else
        constexpr uint8_t legacy_open_count = 0;
#endif
#ifdef __NR_readlink
        constexpr uint8_t legacy_readlink_count = 1;
#else
        constexpr uint8_t legacy_readlink_count = 0;
#endif
#ifdef __NR_fstat64
        constexpr uint8_t legacy_fstat_count = 1;
#else
        constexpr uint8_t legacy_fstat_count = 0;
#endif
        constexpr uint8_t legacy_metadata_count = legacy_readlink_count + legacy_fstat_count;
#ifdef __NR_newfstatat
        constexpr uint8_t new_fstatat_count = 1;
#else
        constexpr uint8_t new_fstatat_count = 0;
#endif
#ifdef __NR_fstatat64
        constexpr uint8_t fstatat64_count = 1;
#else
        constexpr uint8_t fstatat64_count = 0;
#endif
        constexpr uint8_t metadata_count = legacy_metadata_count + new_fstatat_count + fstatat64_count;

        // Traps file opens from instruction addresses in [first, last] unless they carry the replay
        // marker. Addresses are compared as high then low 32-bit words, which also covers ranges
        // that cross a 4 GiB boundary. Jump offsets count the instructions to skip.
        auto instructions = std::to_array<sock_filter>({
            // Allow other architectures.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 0, 15 + legacy_open_count),
            // Route both open ABIs to the same replay and address checks.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
#ifdef __NR_open
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 1, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_open, 0, 13),
#else
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 13),
#endif
            // Allow the handler's replayed opens.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[4])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, replay_marker, 11, 0),
            // Allow addresses below first. A higher high word skips to the upper bound.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first >> 32U), 0, 9),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(first >> 32U), 2, 0),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first), 0, 6),
            // Allow addresses above last. A lower high word traps.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last >> 32U), 4, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(last >> 32U), 0, 2),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last), 1, 0),
            // Trap.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP | trap_tag),
            // Allow.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        });

        sock_fprog program{
            .len = static_cast<unsigned short>(instructions.size()),
            .filter = instructions.data(),
        };

        auto identity_instructions = std::to_array<sock_filter>({
            // Allow other architectures. Legacy syscall checks extend only this dispatch block;
            // all jumps inside the shared address/replay checks below remain unchanged.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 0,
                     21 + legacy_open_count + metadata_count),
            // Route opens to their address check and descriptor metadata to its replay check.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat,
                     3 + legacy_open_count + metadata_count, 0),
#ifdef __NR_open
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_open, 3 + metadata_count, 0),
#endif
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_readlinkat, 15 + metadata_count, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_fstat, 14 + metadata_count, 0),
#ifdef __NR_newfstatat
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_newfstatat,
                     14 + legacy_metadata_count + fstatat64_count, 0),
#endif
#ifdef __NR_fstatat64
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_fstatat64, 14 + legacy_metadata_count, 0),
#endif
#ifdef __NR_readlink
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_readlink, 14 + legacy_fstat_count, 0),
#endif
#ifdef __NR_fstat64
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_fstat64, 14, 0),
#endif
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            // Allow replayed opens.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[4])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, replay_marker, 14, 0),
            // Allow open addresses below first.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first >> 32U), 0, 12),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(first >> 32U), 2, 0),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first), 0, 9),
            // Allow open addresses above last.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last >> 32U), 7, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(last >> 32U), 0, 2),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last), 4, 0),
            // Trap opens in the library's range.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP | trap_tag),
            // Trap descriptor metadata regardless of caller, except our replayed calls.
            // Only descriptors resolving to our replacement files receive a virtual identity.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[4])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, replay_marker, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP | trap_tag),
            // Allow.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        });

        if (preserve_identity) {
            program.len = static_cast<unsigned short>(identity_instructions.size());
            program.filter = identity_instructions.data();
        }

        std::vector<sock_filter> content_instructions;
        if (redirect_contents) {
            // Every trapped call originates in the protected library. Replayed calls originate
            // in this extension, so syscall argument words remain available for 64-bit offsets.
            content_instructions = {
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 1, 0),
                BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            };
            
            constexpr auto calls = std::to_array({__NR_openat, __NR_read, __NR_pread64, __NR_close,
                                              __NR_readlinkat, __NR_fstat,
#ifdef __aarch64__
                                              __NR_mmap,
#else
                                              __NR_mmap2,
#endif
#ifdef __NR_lseek
                                              __NR_lseek,
#endif
#ifdef __NR__llseek
                                              __NR__llseek,
#endif
#ifdef __NR_open
                                              __NR_open,
#endif
#ifdef __NR_newfstatat
                                              __NR_newfstatat,
#endif
#ifdef __NR_fstatat64
                                              __NR_fstatat64,
#endif
#ifdef __NR_fstat64
                                              __NR_fstat64,
#endif
                                             });

            for (const int number : calls) {
                content_instructions.push_back(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(number), 0, 1));
                content_instructions.push_back(BPF_JUMP(BPF_JMP | BPF_JA, 0, 0, 0));
            }

            content_instructions.push_back(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));

            const size_t range_check = content_instructions.size();
            for (size_t index = 5; index < range_check - 1; index += 2)
                element_at(content_instructions, index).k = static_cast<uint32_t>(range_check - index - 1);
            
            // The existing open-only filter already contains the complete address comparison.
            content_instructions.insert(content_instructions.end(), instructions.begin() +
                    6 + legacy_open_count, instructions.end());
            
            program.len = static_cast<unsigned short>(content_instructions.size());
            program.filter = content_instructions.data();
        }

        if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0)
            return std::unexpected(filter_failure{.cause = "errno", .value = errno});

        // Never fall back to a filter that silently covers only the installer thread.
        const long result = syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER,
                                    SECCOMP_FILTER_FLAG_TSYNC, &program);

        if (result < 0) return std::unexpected(filter_failure{.cause = "errno", .value = errno});
        if (result > 0)
            return std::unexpected(
                    filter_failure{.cause = "unsynchronized thread", .value = result});

        return {};
    }

    bool install_sigsys_handler() {
        struct sigaction handler{};
        handler.sa_sigaction = handle_sigsys;
        handler.sa_flags = SA_SIGINFO | SA_ONSTACK;
        
        sigemptyset(&handler.sa_mask);
        return sigaction(SIGSYS, &handler, nullptr) == 0;
    }
} // namespace

bool install_raw_open_redirect(const char *const library,
                               const std::span<const raw_open_mapping> paths,
                               const raw_redirect_options &options) {
    const std::scoped_lock lock(install_mutex);

    if (const redirect_state *const current = active.load(std::memory_order_acquire);
        current != nullptr && current->installed)
        return current->library == library;

    if (paths.empty()) return false;

    auto candidate = std::make_unique<redirect_state>();
    candidate->library = library;
    candidate->paths.assign_range(paths);

    dl_iterate_phdr(find_library, candidate.get());

    candidate->preserve_descriptor_identity = options.preserve_descriptor_identity;
    candidate->redirect_file_contents = options.redirect_file_contents;
    candidate->snapshot_proc_file = options.snapshot_proc_file;
    candidate->bypass_current_thread = options.bypass_current_thread;
    
    if (candidate->snapshot_proc_file != nullptr) {
        const long limit = sysconf(_SC_OPEN_MAX);
        if (limit <= 0) return false;
    
        candidate->installing_process = getpid();
        candidate->descriptor_count = static_cast<size_t>(limit);
        candidate->proc_descriptors.reset(candidate->descriptor_count);

        for (size_t index = 0; index < candidate->descriptor_count; index++)
            candidate->proc_descriptors.slot(index).store(-1, std::memory_order_relaxed);
    }

    if (options.start < options.end) {
        candidate->start = options.start;
        candidate->end = options.end;
    }

    if (const bool readable = path_copying_available(library);
        candidate->libraries_found != 1 || candidate->start >= candidate->end || !readable) {
        LOG_ERROR("Cannot trap %s: %u libraries, path copying %s", library,
                  candidate->libraries_found, readable ? "available" : "unavailable");
        return false;
    }

    if (candidate->preserve_descriptor_identity && !prepare_descriptor_identities(*candidate))
        return false;

    // Publish before installing the handler, together with the previous disposition, so unrelated
    // SIGSYS signals can use it. A signal may still be using the published state, so it is
    // retained from here on, even when installation fails.
    if (sigaction(SIGSYS, nullptr, &candidate->previous_handler) != 0) return false;

    redirect_state *const state = candidate.release();
    active.store(state, std::memory_order_release);
    
    if (!install_sigsys_handler()) return false;

    if (const std::expected<void, filter_failure> filter = install_filter(
                state->start, state->end, state->preserve_descriptor_identity, state->redirect_file_contents);
        !filter) {
        sigaction(SIGSYS, &state->previous_handler, nullptr);
        LOG_ERROR("Installing raw APK file seccomp filter failed: %s %ld", filter.error().cause,
                  filter.error().value);
        return false;
    }

    // Filter installation is irreversible. State and mapping strings live until process exit;
    // forked children inherit them, the signal disposition and the filter.
    state->installed = true;
    state->filters.store(1, std::memory_order_release);
    if (!diagnostics_enabled()) return true;

    start_event_logger(*state);
    LOG_INFO("Raw APK file seccomp filter installed for %s [%p, %p), all threads", library,
             reinterpret_cast<void *>(state->start), reinterpret_cast<void *>(state->end));
    return true;
}

bool raw_redirect_contains_address(const uintptr_t address) {
    const redirect_state *const state = active.load(std::memory_order_acquire);
    return state != nullptr && contains_address(*state, address);
}

unsigned raw_redirect_filter_count() {
    const redirect_state *const state = active.load(std::memory_order_acquire);
    return state == nullptr ? 0 : state->filters.load(std::memory_order_acquire);
}

bool extend_raw_open_redirect(const uintptr_t start, const uintptr_t end) {
    const std::scoped_lock lock(install_mutex);
    redirect_state *const state = active.load(std::memory_order_acquire);
    if (state == nullptr || !state->installed || start >= end) return false;

    if (start >= state->start && end <= state->end) return true;
    for (const executable_range *range = state->unpacked_ranges.load(std::memory_order_acquire);
         range != nullptr; range = range->next)
        if (start >= range->start && end <= range->end) return true;

    // Every replay originates here. Covering this address would recursively trap the handler.
    if (const auto replay = reinterpret_cast<uintptr_t>(&signature_raw_syscall);
        replay >= start && replay <= end) return false;

    auto range = std::make_unique<executable_range>(executable_range{
        .start = start, .end = end,
        .next = state->unpacked_ranges.load(std::memory_order_acquire),
    });
    state->unpacked_ranges.store(range.release(), std::memory_order_release);

    if (const auto result = install_filter(start, end, state->preserve_descriptor_identity,
                                           state->redirect_file_contents); !result) {
        LOG_ERROR("Cannot extend native APK redirection: %s %ld", result.error().cause, result.error().value);
        return false;
    }

    state->filters.fetch_add(1, std::memory_order_release);
    LOG_INFO("Raw APK file redirection covers unpacked code [%p, %p)",
             reinterpret_cast<void *>(start), reinterpret_cast<void *>(end));
    return true;
}

std::optional<long> redirect_native_file_syscall(
        const uintptr_t caller, const int number, const long first, const long second,
        const long third, const long fourth, const long fifth, const long sixth) {
    redirect_state *const state = active.load(std::memory_order_acquire);
    if (state == nullptr || !contains_address(*state, caller) || !supports_syscall(*state, number) ||
        (state->bypass_current_thread != nullptr && state->bypass_current_thread()))
        return std::nullopt;

    const syscall_arguments arguments{
        .first = first, .second = second, .third = third,
        .fourth = fourth, .fifth = fifth, .sixth = sixth,
    };
    return redirect_file_syscall(*state, number, arguments);
}
