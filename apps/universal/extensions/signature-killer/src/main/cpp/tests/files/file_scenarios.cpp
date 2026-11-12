// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "file_test.h"
#include "logging.h"
#include <fcntl.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/ptrace.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>
#include <csignal>
#include <cstddef>
#include <array>
#include <cstring>
#include <utility>
#include <atomic>
#include <thread>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    namespace {
        int (*const open_file)(const char *, int, ...) = open;

    } // namespace

    void file_redirect_test::install_redirect() {
        struct sigaction previous{};
        previous.sa_handler = previous_handler;
        sigemptyset(&previous.sa_mask);
        assert(sigaction(SIGSYS, &previous, nullptr) == 0);
        std::atomic ready{false};
        std::atomic proceed{false};
        std::thread existing_thread([&] {
            ready.store(true, std::memory_order_release);
            while (!proceed.load(std::memory_order_acquire)) std::this_thread::yield();
            const int descriptor = raw_open(__NR_openat, installed_path.c_str());
            expect_contents(descriptor, "original contents");
            assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
        });
        while (!ready.load(std::memory_order_acquire)) std::this_thread::yield();

        std::string library;
        assert(dl_iterate_phdr(find_executable, &library) != 0);
        std::vector paths{
            raw_open_mapping{
                .installed = installed_path.c_str(),
                .replacement = replacement_path.c_str(),
            },
        };
        if (redirect_contents)
            paths.push_back(
                {.installed = large_installed_path.c_str(), .replacement = large_replacement_path.c_str()});
        diagnostics.store(true, std::memory_order_relaxed);
        assert(
            install_raw_open_redirect(library.c_str(), paths,
                                      {.start = reinterpret_cast<uintptr_t>(gateway),
                                       .end = reinterpret_cast<uintptr_t>(gateway) + gateway_size,
                                       .preserve_descriptor_identity = preserve_identity,
                                       .redirect_file_contents = redirect_contents,
                                       .snapshot_proc_file = redirect_contents ? proc_snapshot : nullptr,
                                       .install_after_fork = install_after_fork}));
        diagnostics.store(false, std::memory_order_relaxed);
        // The thread started before the fork must still receive the filter the handler installs.
        if (install_after_fork) check_traced_fork();
        proceed.store(true, std::memory_order_release);
        existing_thread.join();
    }

    int file_redirect_test::check_contents() const {
        assert(raise(SIGSYS) == 0);
        assert(previous_signal == 1);
        const int redirected = raw_open(__NR_openat, installed_path.c_str());
        expect_contents(redirected, "original contents");
        expect_content_offsets(redirected);
        if (redirect_contents) {
            expect_large_file_contents(large_installed_path.c_str(), page_size);
            expect_proc_snapshot("/proc/self/maps", "maps snapshot", "maps");
            const auto status = "/proc/" + std::to_string(getpid()) + "/status";
            expect_proc_snapshot(status.c_str(), "status snapshot", "status");
            expect_mapped_contents(redirected, page_size);
            expect_contents(installed_descriptor, "original contents");
            const int duplicate = dup(installed_descriptor);
            assert(duplicate >= 0);
            assert(lseek(duplicate, 0, SEEK_SET) == 0);
            expect_contents(duplicate, "original contents");
            expect_mapped_contents(duplicate, page_size);
            assert(close(duplicate) == 0);

            const std::string alias = "/proc/self/fd/" + std::to_string(installed_descriptor);
            const int reopened = raw_open(__NR_openat, alias.c_str());
            expect_contents(reopened, "original contents");
            assert(close(reopened) == 0);

            // Reuse a previously redirected descriptor through libc, outside the trapped range.
            const int recycled = raw_open(__NR_openat, installed_path.c_str());
            assert(close(recycled) == 0);
            const int unrelated = open_file(unrelated_path.c_str(), O_RDONLY | O_CLOEXEC);
            assert(unrelated == recycled);
            expect_contents(unrelated, "unrelated contents");
            assert(close(unrelated) == 0);
        }
        return redirected;
    }

    // Pairip's watchdog child stacks a filter that answers openat and rt_sigreturn with chosen errnos
    // and then checks them. The redirect's trap outranks those errnos, so the child must still see
    // its own errno and exit normally.
    void file_redirect_test::check_forked_child_filter() const {
        // A raw clone, like Pairip's, leaves bionic's cached pid stale in the child.
        const long child = syscall(__NR_clone, SIGCHLD, 0, 0, 0, 0);
        assert(child >= 0);
        if (child == 0) {
            constexpr uint32_t child_errno = 3442;
            std::array instructions = {
                sock_filter BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
                sock_filter BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 1, 0),
                sock_filter BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_rt_sigreturn, 0, 1),
                sock_filter BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | child_errno),
                sock_filter BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            };
            const sock_fprog program{.len = instructions.size(), .filter = instructions.data()};
            if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) _exit(2);
            if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program) != 0) _exit(3);

            const long result = protected_syscall(__NR_openat, AT_FDCWD, reinterpret_cast<long>(installed_path.c_str()),
                                                  O_RDONLY | O_CLOEXEC, 0, 0, 0);
            _exit(result == -static_cast<long>(child_errno) ? 0 : 4);
        }

        int status = 0;
        assert(waitpid(static_cast<pid_t>(child), &status, 0) == child);
        assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    }
    // Pairip's newer watchdog child traces its own syscalls: it stops for its parent, stacks a
    // SECCOMP_RET_TRACE filter, and expects each call to reach the tracer and then run. An inherited
    // trap would outrank that filter and turn the call into a SIGSYS the tracer discards.
    void file_redirect_test::check_traced_fork() const {
        // Crash reporters call bionic's fork() inside handlers that block SIGSYS. It must pass untrapped
        // and leave the filter held back for the protected library's raw fork.
        sigset_t blocked{};
        sigset_t previous{};
        sigemptyset(&blocked);
        sigaddset(&blocked, SIGSYS);
        assert(pthread_sigmask(SIG_BLOCK, &blocked, &previous) == 0);
        const pid_t reporter = fork();
        assert(reporter >= 0);
        if (reporter == 0) _exit(0);
        assert(pthread_sigmask(SIG_SETMASK, &previous, nullptr) == 0);
        int reporter_status = 0;
        assert(waitpid(reporter, &reporter_status, 0) == reporter);
        assert(WIFEXITED(reporter_status) && WEXITSTATUS(reporter_status) == 0);

        // Before the protected fork, the held-back filter must not redirect anything yet.
        const int before = raw_open(__NR_openat, installed_path.c_str());
        expect_contents(before, "installed");
        assert(close(before) == 0);

        const long child = protected_syscall(__NR_clone, SIGCHLD, 0, 0, 0, 0, 0);
        assert(child >= 0);
        if (child == 0) {
            if (ptrace(PTRACE_TRACEME, 0, nullptr, nullptr) != 0) _exit(2);
            // A raw clone leaves bionic's cached pid stale, so raise() would signal the parent.
            if (syscall(__NR_kill, syscall(__NR_getpid), SIGSTOP) != 0) _exit(3);
            std::array instructions = {
                sock_filter BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
                sock_filter BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 0, 1),
                sock_filter BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRACE),
                sock_filter BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            };
            const sock_fprog program{.len = instructions.size(), .filter = instructions.data()};
            if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) _exit(4);
            if (prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, &program) != 0) _exit(5);

            const long descriptor = protected_syscall(__NR_openat, AT_FDCWD,
                                                      reinterpret_cast<long>(installed_path.c_str()),
                                                      O_RDONLY | O_CLOEXEC, 0, 0, 0);
            _exit(descriptor >= 0 ? 0 : 6);
        }

        int status = 0;
        assert(waitpid(static_cast<pid_t>(child), &status, __WALL) == child);
        assert(WIFSTOPPED(status) && WSTOPSIG(status) == SIGSTOP);
        assert(ptrace(PTRACE_SETOPTIONS, child, nullptr, PTRACE_O_TRACESECCOMP | PTRACE_O_EXITKILL) == 0);
        assert(ptrace(PTRACE_CONT, child, nullptr, nullptr) == 0);

        // A seccomp stop reports SIGTRAP with the event number above the signal bits.
        constexpr unsigned seccomp_stop =
            static_cast<unsigned>(SIGTRAP) | static_cast<unsigned>(PTRACE_EVENT_SECCOMP) << 8U;
        unsigned seccomp_stops = 0;
        while (true) {
            assert(waitpid(static_cast<pid_t>(child), &status, __WALL) == child);
            if (!WIFSTOPPED(status)) break;
            // Any signal stop is the regression; resuming without it, like Pairip, loses the call.
            assert(static_cast<unsigned>(status) >> 8U == seccomp_stop);
            seccomp_stops++;
            assert(ptrace(PTRACE_CONT, child, nullptr, nullptr) == 0);
        }
        assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
        assert(seccomp_stops == 1);
    }
} // namespace signature::tests
