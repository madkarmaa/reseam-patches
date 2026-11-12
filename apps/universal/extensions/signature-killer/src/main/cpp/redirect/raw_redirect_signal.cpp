// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <linux/seccomp.h>
#include <sys/syscall.h>
#include <sys/ucontext.h>
#include <csignal>
#include <cstdlib>
#include <cstring>
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

        bool is_fork_trap(const redirect_state &state, const siginfo_t &info) {
            return info.si_code == SYS_SECCOMP && std::cmp_equal(info.si_errno, trap_tag) &&
                   info.si_syscall == __NR_clone && info.si_arch == syscall_arch &&
                   !state.deferred_filter.empty();
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

        // A raw clone() leaves libc's cached pid stale, so ask the kernel.
        bool in_forked_child(const redirect_state &state) {
            return signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0) != state.installing_process;
        }

        // Each record the kernel saves after the general registers starts with this header.
        struct context_record {
            uint32_t magic;
            uint32_t size;
        };

#ifdef __aarch64__
        constexpr uint32_t floating_point_magic = FPSIMD_MAGIC;
        constexpr size_t floating_point_payload_offset = 0;
#else
        // The kernel's vfp_sigframe: the record header, then d0-d31 and fpscr.
        constexpr uint32_t floating_point_magic = 0x56465001;
        constexpr size_t floating_point_payload_offset = sizeof(context_record);
#endif

        // Locates the saved floating-point registers the kernel placed after the general registers.
        const void *saved_floating_point(const ucontext_t &context) {
#ifdef __aarch64__
            const auto *const records = context.uc_mcontext.__reserved;
            constexpr size_t capacity = sizeof(context.uc_mcontext.__reserved);
#else
            const auto *const records = reinterpret_cast<const uint8_t *>(context.uc_regspace);
            constexpr size_t capacity = sizeof(context.uc_regspace);
#endif
            for (size_t offset = 0; offset + sizeof(context_record) <= capacity;) {
                context_record record{};
                memcpy(&record, records + offset, sizeof(record));
                if (record.magic == 0 || record.size == 0) return nullptr;
                if (record.magic == floating_point_magic) return records + offset + floating_point_payload_offset;
                offset += record.size;
            }
            return nullptr;
        }

        // Continues the trapped code without rt_sigreturn, which a forked child's own seccomp filter can
        // answer with an errno. On arm64 the trap sits right after the protected library's svc, where
        // x16 and x17 are dead.
        [[noreturn]] void resume_without_sigreturn(ucontext_t &context) {
            signature_raw_syscall(__NR_rt_sigprocmask, SIG_SETMASK, reinterpret_cast<long>(&context.uc_sigmask64), 0,
                                  sizeof(context.uc_sigmask64), 0, 0);
#ifdef __arm__
            if (constexpr unsigned long thumb_state = 1UL << 5U; (context.uc_mcontext.arm_cpsr & thumb_state) != 0)
                context.uc_mcontext.arm_pc |= 1U;
#endif
            signature_resume_context(&context.uc_mcontext, saved_floating_point(context));
        }

        // Installs the held-back redirect filter once, in the process that prepared it.
        void install_deferred_filter(redirect_state &state) {
            if (in_forked_child(state) || !state.filter_deferred.exchange(false, std::memory_order_acq_rel)) return;
            if (!install_program(state.deferred_filter)) abort();
            state.filters.fetch_add(1, std::memory_order_release);
        }

        // Forks for the caller, so the child starts without the redirect filter and the parent gets it
        // before the caller continues. The fork filter only traps plain forks, which keep the caller's
        // stack, so both processes return through this frame.
        void fork_for_library(redirect_state &state, mcontext_t &registers) {
            const auto arguments = read_syscall_arguments(registers);
            const long child = signature_raw_syscall(__NR_clone, arguments.first, arguments.second, arguments.third,
                                                     arguments.fourth, arguments.fifth, replay_marker);
            if (child > 0) install_deferred_filter(state);
            set_syscall_result(registers, child);
        }

        // Replays intercepted file syscalls, changing only mapped paths or their descriptor metadata.
        void handle_sigsys(const int signal, siginfo_t *const info, void *const context) {
            redirect_state *const state = active.load(std::memory_order_acquire);
            if (is_fork_trap(*state, *info)) {
                fork_for_library(*state, static_cast<ucontext_t *>(context)->uc_mcontext);
                return;
            }

            if (!is_redirect_trap(*state, *info)) {
                forward_to_previous_handler(state->previous_handler, signal, info, context);
                return;
            }

            mcontext_t &registers = static_cast<ucontext_t *>(context)->uc_mcontext;
            const auto arguments = read_syscall_arguments(registers);

            // Pairip's watchdog child stacks its own filter, which answers these calls with chosen errnos
            // and refuses rt_sigreturn. This filter's trap outranks those errnos, so replay the call
            // unchanged for the child's filter to answer, and continue without returning.
            if (const bool forked_child = in_forked_child(*state);
                forked_child || (state->bypass_current_thread != nullptr && state->bypass_current_thread())) {
                const long fifth = state->redirect_file_contents ? arguments.fifth : replay_marker;
                set_syscall_result(registers, signature_raw_syscall(
                                                  info->si_syscall, arguments.first, arguments.second,
                                                  arguments.third, arguments.fourth, fifth, arguments.sixth));
                if (forked_child) resume_without_sigreturn(*static_cast<ucontext_t *>(context));
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
