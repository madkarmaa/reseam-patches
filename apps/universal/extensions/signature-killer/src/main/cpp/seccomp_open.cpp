// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Each file includes what it uses, even when another header already provides it.
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
#elifdef __x86_64__
    constexpr uint32_t syscall_arch = AUDIT_ARCH_X86_64;
#elifdef __i386__
    constexpr uint32_t syscall_arch = AUDIT_ARCH_I386;
#else
#error Unsupported raw syscall architecture
#endif

    // Passed in the unused fifth argument of intercepted syscalls, so the filter lets the
    // handler's own replayed calls through.
    constexpr uint32_t replay_marker = 0x534b4150;
    // Reported in si_errno, so the handler recognizes traps raised by this filter.
    constexpr uint16_t trap_tag = 0x534b;
    // Limits redirected and preserved syscall events separately so diagnostics stay bounded.
    constexpr unsigned reported_event_limit = 32;

    struct descriptor_identity {
        std::string resolved_replacement;
        struct stat installed{};
    };

    struct redirect_state {
        std::string library;
        // The span of the library's executable segments.
        uintptr_t start = UINTPTR_MAX;
        uintptr_t end = 0;
        // Loaded libraries with that basename. Exactly one is required.
        unsigned libraries_found = 0;
        // Set once the filter is in place. An installed state is never replaced.
        bool installed = false;
        std::vector<raw_open_mapping> paths;
        bool preserve_descriptor_identity = false;
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

    // Widens the state's range to the executable segments of each loaded library with its name.
    // dl_iterate_phdr fixes the signature.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    int find_library(dl_phdr_info *const info, size_t /*info_size*/, void *const data) {
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
    }

    // The kernel copies the path, so an invalid pointer returns EFAULT instead of faulting in the
    // signal handler. A partial copy is enough when it includes a matching path's terminating NUL.
    // The kernel writes through iov_base, which the checker cannot infer.
    // NOLINTNEXTLINE(misc-const-correctness)
    long read_path(const char *const path, char *const buffer, const size_t size) {
        iovec local{.iov_base = buffer, .iov_len = size};
        // process_vm_readv only reads the remote buffer, despite its non-const iovec.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-const-cast)
        iovec remote{.iov_base = const_cast<char *>(path), .iov_len = size};
        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_readv, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    // The handler relies on read_path, which a sandbox may forbid.
    bool path_copying_available(const char *const sample) {
        char probe = 0;
        return read_path(sample, &probe, 1) == 1 && probe == sample[0];
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

            std::array<char, 64> descriptor{};
            const int formatted = snprintf(descriptor.data(), descriptor.size(),
                                           "/proc/self/fd/%d", replacement);
            std::array<char, PATH_MAX> resolved{};
            const ssize_t length = formatted > 0 && std::cmp_less(formatted, descriptor.size())
                    ? readlink(descriptor.data(), resolved.data(), resolved.size() - 1) : -1;
            close(replacement);
            if (length <= 0) return false;

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

    // Logs the events the signal handler writes to the pipe, since the handler itself cannot log.
    // pthread_create fixes the signature.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    void *log_syscall_events(void *const data) {
        const int descriptor = static_cast<int>(reinterpret_cast<intptr_t>(data));
        syscall_event event{};
        while (true) {
            const ssize_t length = read(descriptor, &event, sizeof(event));
            if (length < 0 && errno == EINTR) continue;
            if (length != sizeof(event)) break;

            const auto *name = "openat";
            if (event.syscall == __NR_readlinkat) name = "readlinkat";
            else if (event.syscall == __NR_fstat) name = "fstat";
#ifdef __NR_open
            else if (event.syscall == __NR_open) name = "open";
#endif
#ifdef __NR_readlink
            else if (event.syscall == __NR_readlink) name = "readlink";
#endif
#ifdef __NR_fstat64
            else if (event.syscall == __NR_fstat64) name = "fstat64";
#endif
            LOG_INFO("Raw %s %s: mapping %d, kernel result %ld", name,
                     event.mapping >= 0 ? "redirected" : "preserved", event.mapping, event.result);
        }
        close(descriptor);
        return nullptr;
    }

    // Without a logger thread the handler reports nothing.
    void start_event_logger(redirect_state &state) {
        std::array<int, 2> descriptors{};
        if (pipe2(descriptors.data(), O_CLOEXEC) != 0) return;

        // A full pipe must drop events rather than block the trapped thread.
        pthread_t logger{};
        const auto [read_pipe, write_pipe] = descriptors;
        if (fcntl(write_pipe, F_SETFL, O_NONBLOCK) == 0 &&
            pthread_create(&logger, nullptr, log_syscall_events,
                           reinterpret_cast<void *>(static_cast<intptr_t>(read_pipe))) == 0) {
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

    bool is_redirect_trap(const redirect_state &state, const siginfo_t &info) {
        const auto caller = reinterpret_cast<uintptr_t>(info.si_call_addr);
        const bool opens_file = is_open_syscall(info.si_syscall);
        
        const bool supported = opens_file || (state.preserve_descriptor_identity &&
                (is_readlink_syscall(info.si_syscall) || is_fstat_syscall(info.si_syscall)));
        
        return info.si_code == SYS_SECCOMP && std::cmp_equal(info.si_errno, trap_tag) &&
               supported && info.si_arch == syscall_arch &&
               (!opens_file || (caller >= state.start && caller <= state.end));
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
    };

    syscall_arguments read_syscall_arguments(const mcontext_t &registers) {
#ifdef __aarch64__
        return {
            .first = static_cast<long>(registers.regs[0]),
            .second = static_cast<long>(registers.regs[1]),
            .third = static_cast<long>(registers.regs[2]),
            .fourth = static_cast<long>(registers.regs[3]),
        };
#elifdef __arm__
        return {
            .first = static_cast<long>(registers.arm_r0),
            .second = static_cast<long>(registers.arm_r1),
            .third = static_cast<long>(registers.arm_r2),
            .fourth = static_cast<long>(registers.arm_r3),
        };
#elifdef __x86_64__
        return {
            .first = registers.gregs[REG_RDI],
            .second = registers.gregs[REG_RSI],
            .third = registers.gregs[REG_RDX],
            .fourth = registers.gregs[REG_R10],
        };
#elifdef __i386__
        return {
            .first = registers.gregs[REG_EBX],
            .second = registers.gregs[REG_ECX],
            .third = registers.gregs[REG_EDX],
            .fourth = registers.gregs[REG_ESI],
        };
#endif
    }

    void set_syscall_result(mcontext_t &registers, const long result) {
#ifdef __aarch64__
        registers.regs[0] = result;
#elifdef __arm__
        registers.arm_r0 = result;
#elifdef __x86_64__
        registers.gregs[REG_RAX] = result;
#elifdef __i386__
        registers.gregs[REG_EAX] = result;
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
        for (size_t index = 0; index < state.paths.size(); index++) {
            // The loop bounds the index; at() could throw on the signal stack.
            // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access)
            if (copied_path_equals(copied, copied_length, state.paths[index].installed))
                return static_cast<int>(index);
        }
        
        return -1;
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

    long write_memory(void *const destination, const void *const source, const size_t length) {
        // process_vm_writev reads the local buffer despite the non-const iovec API.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-const-cast)
        iovec local{.iov_base = const_cast<void *>(source), .iov_len = length};
        iovec remote{.iov_base = destination, .iov_len = length};
        
        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_writev, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    int find_replacement_mapping(const redirect_state &state, const char *const path,
                                  const size_t length) {
        for (size_t index = 0; index < state.identities.size(); index++) {
            // The loop bounds the index; this path cannot throw from a signal handler.
            // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access)
            if (copied_path_equals(path, length, state.identities[index].resolved_replacement.c_str()))
                return static_cast<int>(index);
        }
        
        return -1;
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
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-member-init)
        std::array<char, PATH_MAX> resolved;
        const long length = signature_raw_syscall(
                __NR_readlinkat, AT_FDCWD, reinterpret_cast<long>(path.data()),
                reinterpret_cast<long>(resolved.data()), resolved.size() - 1, replay_marker, 0);
        
        if (length <= 0) return -1;
        
        // The syscall's capacity reserves this terminator inside the array.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access,cppcoreguidelines-pro-bounds-constant-array-index)
        resolved[static_cast<size_t>(length)] = '\0';
        return find_replacement_mapping(state, resolved.data(), length + 1);
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
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-member-init)
        std::array<char, PATH_MAX> resolved;
        long resolved_length = result;
        
        // A short caller buffer may contain only a prefix of the replacement path. Resolve the
        // full target for matching, while keeping the original result for unrelated descriptors.
        if (std::cmp_equal(result, size)) {
            resolved_length = replay(resolved.data(), resolved.size() - 1);
            if (resolved_length <= 0 || resolved_length >= PATH_MAX) return result;
        } else if (read_path(buffer, resolved.data(), result) != result) {
            return -EFAULT;
        }
        
        // Both reads are bounded above by PATH_MAX - 1.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access,cppcoreguidelines-pro-bounds-constant-array-index)
        resolved[static_cast<size_t>(resolved_length)] = '\0';
        mapping = find_replacement_mapping(state, resolved.data(), resolved_length + 1);
        if (mapping < 0) return result;

        // find_replacement_mapping returns an index into the parallel path and identity tables.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access)
        const char *const installed = state.paths[mapping].installed;
        const size_t length = std::min(size, strlen(installed));
        
        if (write_memory(buffer, installed, length) != static_cast<long>(length)) return -EFAULT;
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
        // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access)
        const auto &installed = state.identities[mapping].installed;
        
        // Preserve the real inode and content metadata, which must agree with memory mappings.
        // Only ownership distinguishes this copy from an installed, system-owned APK.
        value.st_uid = static_cast<decltype(value.st_uid)>(installed.st_uid);
        value.st_gid = static_cast<decltype(value.st_gid)>(installed.st_gid);
        
        if (write_memory(buffer, &value, sizeof(value)) !=
            static_cast<long>(sizeof(value)))
            return -EFAULT;
        
        return 0;
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
        const int number = info->si_syscall;
        
        if (state->preserve_descriptor_identity && !is_open_syscall(number)) {
            int mapping = -1;
            long result = 0;
            if (is_readlink_syscall(number))
                result = redirect_readlink(*state, number, arguments, mapping);
#ifdef __NR_fstat64
            else if (number == __NR_fstat64)
                result = redirect_fstat<signature_kernel_stat64>(
                        *state, number, arguments.first,
                        reinterpret_cast<void *>(arguments.second), mapping);
#endif
            else
                result = redirect_fstat<signature_kernel_stat>(
                        *state, number, arguments.first,
                        reinterpret_cast<void *>(arguments.second), mapping);
            
            set_syscall_result(registers, result);
            report_event(*state, info->si_syscall, result, mapping);
            return;
        }
        
        const bool has_directory = number == __NR_openat;
        const auto *const requested_path = reinterpret_cast<const char *>(
                has_directory ? arguments.second : arguments.first);

        // Zeroing would cost a full PATH_MAX write per trapped open; read_path fills what is used.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-member-init)
        std::array<char, PATH_MAX> copied;
        const long length = read_path(requested_path, copied.data(), copied.size());
        const int mapping = find_mapping(*state, copied.data(), length);
        // find_mapping returns a validated path-table index, or -1 for an unmatched path.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-bounds-avoid-unchecked-container-access)
        const char *const path = mapping >= 0 ? state->paths[mapping].replacement : requested_path;

        const long result = has_directory ? signature_raw_syscall(__NR_openat, arguments.first,
                                                  reinterpret_cast<long>(path), arguments.third,
                                                  arguments.fourth,
                                                  replay_marker, 0)
                : signature_raw_syscall(number, reinterpret_cast<long>(path), arguments.second,
                                        arguments.third, 0, replay_marker, 0);
        
        set_syscall_result(registers, result);
        report_event(*state, info->si_syscall, result, mapping);
    }

    struct filter_failure {
        const char *cause;
        long value;
    };

    // Fails with an errno, or with the ID of a thread that could not synchronize.
    std::expected<void, filter_failure> install_filter(const uintptr_t start, const uintptr_t end,
                                                       const bool preserve_identity) {
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
                     21 + legacy_open_count + legacy_metadata_count),
            // Route opens to their address check and descriptor metadata to its replay check.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat,
                     3 + legacy_open_count + legacy_metadata_count, 0),
#ifdef __NR_open
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_open, 3 + legacy_metadata_count, 0),
#endif
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_readlinkat, 15 + legacy_metadata_count, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_fstat, 14 + legacy_metadata_count, 0),
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
                state->start, state->end, state->preserve_descriptor_identity);
        !filter) {
        sigaction(SIGSYS, &state->previous_handler, nullptr);
        LOG_ERROR("Installing raw APK file seccomp filter failed: %s %ld", filter.error().cause,
                  filter.error().value);
        return false;
    }

    // Filter installation is irreversible. State and mapping strings live until process exit;
    // forked children inherit them, the signal disposition and the filter.
    state->installed = true;
    if (!diagnostics_enabled()) return true;

    start_event_logger(*state);
    LOG_INFO("Raw APK file seccomp filter installed for %s [%p, %p), all threads", library,
             reinterpret_cast<void *>(state->start), reinterpret_cast<void *>(state->end));
    return true;
}
