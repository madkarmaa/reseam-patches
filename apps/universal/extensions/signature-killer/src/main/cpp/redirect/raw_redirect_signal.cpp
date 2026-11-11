// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <linux/seccomp.h>
#include <sys/syscall.h>
#include <sys/ucontext.h>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        // Signal-stack operations must remain lock-free, bounded, and free of allocation or logging.
        bool is_redirect_trap(const redirect_state &state, const siginfo_t &info) {
            const auto caller = reinterpret_cast<uintptr_t>(info.si_call_addr);
            const bool opens_file = is_open_syscall(info.si_syscall);

            return info.si_code == SYS_SECCOMP && std::cmp_equal(info.si_errno, trap_tag) &&
                   supports_syscall(state, info.si_syscall) && info.si_arch == syscall_arch &&
                   (!(opens_file || state.redirect_file_contents) || contains_address(state, caller));
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
            else previous.sa_handler(signal);
        }

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
                set_syscall_result(registers, signature_raw_syscall(
                                                  info->si_syscall, arguments.first, arguments.second,
                                                  arguments.third, arguments.fourth, fifth, arguments.sixth));
                return;
            }
            set_syscall_result(registers, redirect_file_syscall(*state, info->si_syscall, arguments));
        }

    } // namespace

    bool install_sigsys_handler() {
        struct sigaction handler{};
        handler.sa_sigaction = handle_sigsys;
        handler.sa_flags = SA_SIGINFO | SA_ONSTACK;

        sigemptyset(&handler.sa_mask);
        return sigaction(SIGSYS, &handler, nullptr) == 0;
    }

} // namespace signature::redirect
