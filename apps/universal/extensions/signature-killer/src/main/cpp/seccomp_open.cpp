// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Each file includes what it uses, even when another header already provides it.
// ReSharper disable CppUnusedIncludeDirective
#include "seccomp_open.h"

#include <fcntl.h>
#include <link.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/ucontext.h>
#include <sys/uio.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <climits>
#include <csignal>
#include <cstddef>
#include <cstdint>
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

    // Passed in the otherwise unused fifth openat argument, so the filter lets the handler's own
    // replayed openat through.
    constexpr uint32_t replay_marker = 0x534b4150;
    // Reported in si_errno, so the handler recognizes traps raised by this filter.
    constexpr uint16_t trap_tag = 0x534b;
    // The handler reports only the first opens, so the logger thread cannot flood logcat.
    constexpr unsigned reported_event_limit = 32;

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
        // Receives every SIGSYS this filter did not raise.
        struct sigaction previous_handler{};
        // The write end of the logger thread's pipe, or -1 when diagnostics are off.
        std::atomic<int> event_pipe{-1};
        std::atomic<unsigned> reported_events{0};
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
    int find_library(dl_phdr_info *const info, size_t /*info_size*/, void *const data) {
        auto &state = *static_cast<redirect_state *>(data);
        const char *const slash = strrchr(info->dlpi_name, '/');
        if (const char *const basename = slash == nullptr ? info->dlpi_name : slash + 1;
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

    struct open_event {
        long result;
        // A negative index means the original path was preserved.
        int mapping;
    };

    // Logs the events the signal handler writes to the pipe, since the handler itself cannot log.
    // pthread_create fixes the signature.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    void *log_open_events(void *const data) {
        const int descriptor = static_cast<int>(reinterpret_cast<intptr_t>(data));
        open_event event{};
        while (true) {
            const ssize_t length = read(descriptor, &event, sizeof(event));
            if (length < 0 && errno == EINTR) continue;
            if (length != sizeof(event)) break;

            LOG_INFO("Raw openat %s: mapping %d, kernel result %ld",
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
        if (fcntl(descriptors[1], F_SETFL, O_NONBLOCK) == 0 &&
            pthread_create(&logger, nullptr, log_open_events,
                           reinterpret_cast<void *>(static_cast<intptr_t>(descriptors[0]))) == 0) {
            pthread_detach(logger);
            state.event_pipe.store(descriptors[1], std::memory_order_relaxed);
            return;
        }
        close(descriptors[0]);
        close(descriptors[1]);
    }

    // Everything from here to handle_sigsys runs on the signal stack. Every operation there is a
    // lock-free atomic, a bounded memory access, or a direct syscall. In particular, logging,
    // allocation, JNI and the libc file hooks never run on this stack.

    bool is_redirect_trap(const redirect_state &state, const siginfo_t &info) {
        const auto caller = reinterpret_cast<uintptr_t>(info.si_call_addr);
        return info.si_code == SYS_SECCOMP && std::cmp_equal(info.si_errno, trap_tag) &&
               info.si_syscall == __NR_openat && info.si_arch == syscall_arch &&
               caller >= state.start && caller <= state.end;
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

        if ((previous.sa_flags & SA_SIGINFO) != 0)
            previous.sa_sigaction(signal, info, context);
        else
            previous.sa_handler(signal);
    }

    struct openat_arguments {
        long directory;
        const char *path;
        long flags;
        long mode;
    };

    openat_arguments read_openat_arguments(const mcontext_t &registers) {
#ifdef __aarch64__
        return {
            .directory = static_cast<long>(registers.regs[0]),
            .path = reinterpret_cast<const char *>(registers.regs[1]),
            .flags = static_cast<long>(registers.regs[2]),
            .mode = static_cast<long>(registers.regs[3]),
        };
#elifdef __arm__
        return {
            .directory = static_cast<long>(registers.arm_r0),
            .path = reinterpret_cast<const char *>(registers.arm_r1),
            .flags = static_cast<long>(registers.arm_r2),
            .mode = static_cast<long>(registers.arm_r3),
        };
#elifdef __x86_64__
        return {
            .directory = registers.gregs[REG_RDI],
            .path = reinterpret_cast<const char *>(registers.gregs[REG_RSI]),
            .flags = registers.gregs[REG_RDX],
            .mode = registers.gregs[REG_R10],
        };
#elifdef __i386__
        return {
            .directory = registers.gregs[REG_EBX],
            .path = reinterpret_cast<const char *>(registers.gregs[REG_ECX]),
            .flags = registers.gregs[REG_EDX],
            .mode = registers.gregs[REG_ESI],
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
            if (copied_path_equals(copied, copied_length, state.paths[index].installed))
                return static_cast<int>(index);
        }
        return -1;
    }

    // Hands the event to the logger thread. A full pipe drops it.
    void report_event(redirect_state &state, const long result, const int mapping) {
        const int event_pipe = state.event_pipe.load(std::memory_order_relaxed);
        if (event_pipe < 0) return;
        if (state.reported_events.fetch_add(1, std::memory_order_relaxed) >= reported_event_limit)
            return;

        open_event event{.result = result, .mapping = mapping};
        signature_raw_syscall(__NR_write, event_pipe, reinterpret_cast<long>(&event),
                              sizeof(event), 0, 0, 0);
    }

    // Replays a trapped openat with the replacement path, or the original one when nothing
    // matches, and returns the kernel's result as the syscall's.
    void handle_sigsys(const int signal, siginfo_t *const info, void *const context) {
        redirect_state *const state = active.load(std::memory_order_acquire);
        if (!is_redirect_trap(*state, *info)) {
            forward_to_previous_handler(state->previous_handler, signal, info, context);
            return;
        }

        mcontext_t &registers = static_cast<ucontext_t *>(context)->uc_mcontext;
        const auto [directory, requested_path, flags, mode] = read_openat_arguments(registers);

        // Zeroing would cost a full PATH_MAX write per trapped open; read_path fills what is used.
        // NOLINTNEXTLINE(cppcoreguidelines-pro-type-member-init)
        std::array<char, PATH_MAX> copied;
        const long length = read_path(requested_path, copied.data(), copied.size());
        const int mapping = find_mapping(*state, copied.data(), length);
        const char *const path = mapping >= 0 ? state->paths[mapping].replacement : requested_path;

        const long result = signature_raw_syscall(__NR_openat, directory,
                                                  reinterpret_cast<long>(path), flags, mode,
                                                  replay_marker, 0);
        set_syscall_result(registers, result);
        report_event(*state, result, mapping);
    }

    struct filter_failure {
        const char *cause;
        long value;
    };

    // Fails with an errno, or with the ID of a thread that could not synchronize.
    std::expected<void, filter_failure> install_filter(const uintptr_t start, const uintptr_t end) {
        const uint64_t first = start;
        const uint64_t last = end;
        // Traps openat from instruction addresses in [first, last] unless it carries the replay
        // marker. Addresses are compared as high then low 32-bit words, which also covers ranges
        // that cross a 4 GiB boundary. Jump offsets count the instructions to skip.
        auto instructions = std::to_array<sock_filter>({
            // 0-1: Allow other architectures.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 0, 15),
            // 2-3: Allow other syscalls.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 13),
            // 4-5: Allow the handler's replayed openat.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[4])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, replay_marker, 11, 0),
            // 6-10: Allow addresses below first. A higher high word skips to the upper bound.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first >> 32), 0, 9),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(first >> 32), 2, 0),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGE | BPF_K, static_cast<uint32_t>(first), 0, 6),
            // 11-15: Allow addresses above last. A lower high word traps.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer) + 4),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last >> 32), 4, 0),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(last >> 32), 0, 2),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, instruction_pointer)),
            BPF_JUMP(BPF_JMP | BPF_JGT | BPF_K, static_cast<uint32_t>(last), 1, 0),
            // 16: Trap.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP | trap_tag),
            // 17: Allow.
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
        });
        sock_fprog program{
            .len = static_cast<unsigned short>(instructions.size()),
            .filter = instructions.data(),
        };

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
                               const std::span<const raw_open_mapping> paths) {
    const std::scoped_lock lock(install_mutex);
    if (const redirect_state *const current = active.load(std::memory_order_acquire);
        current != nullptr && current->installed)
        return current->library == library;
    if (paths.empty()) return false;

    auto candidate = std::make_unique<redirect_state>();
    candidate->library = library;
    candidate->paths.assign_range(paths);
    dl_iterate_phdr(find_library, candidate.get());
    if (const bool readable = path_copying_available(library);
        candidate->libraries_found != 1 || candidate->start >= candidate->end || !readable) {
        LOG_ERROR("Cannot trap %s: %u libraries, path copying %s", library,
                  candidate->libraries_found, readable ? "available" : "unavailable");
        return false;
    }

    // Publish before installing the handler, together with the previous disposition, so unrelated
    // SIGSYS signals can use it. A signal may still be using the published state, so it is
    // retained from here on, even when installation fails.
    if (sigaction(SIGSYS, nullptr, &candidate->previous_handler) != 0) return false;
    redirect_state *const state = candidate.release();
    active.store(state, std::memory_order_release);
    if (!install_sigsys_handler()) return false;

    if (const std::expected<void, filter_failure> filter = install_filter(state->start, state->end);
        !filter) {
        sigaction(SIGSYS, &state->previous_handler, nullptr);
        LOG_ERROR("Installing raw openat seccomp filter failed: %s %ld", filter.error().cause,
                  filter.error().value);
        return false;
    }

    // Filter installation is irreversible. State and mapping strings live until process exit;
    // forked children inherit them, the signal disposition and the filter.
    state->installed = true;
    if (!diagnostics_enabled()) return true;

    start_event_logger(*state);
    LOG_INFO("Raw openat seccomp filter installed for %s [%p, %p), all threads", library,
             reinterpret_cast<void *>(state->start), reinterpret_cast<void *>(state->end));
    return true;
}
