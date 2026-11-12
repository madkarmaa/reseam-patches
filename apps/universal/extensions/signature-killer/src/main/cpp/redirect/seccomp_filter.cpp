// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sched.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <array>
#include <cerrno>
#include <cstddef>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        // Traps calls from instruction addresses in [first, last] and allows all others. Addresses are
        // compared as high then low 32-bit words, which also covers ranges that cross a 4 GiB
        // boundary. Jump offsets count the instructions to skip and stay inside this block.
        std::array<sock_filter, 12> address_check(const uint64_t first, const uint64_t last) {
            return std::to_array<sock_filter>({
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
        }

    } // namespace

    std::vector<sock_filter> build_filter(const uintptr_t start, const uintptr_t end, const bool preserve_identity,
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
        const auto library_range = address_check(first, last);

        if (redirect_contents) {
            // Every trapped call originates in the protected library. Replayed calls originate
            // in this extension, so syscall argument words remain available for 64-bit offsets.
            std::vector<sock_filter> instructions = {
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 1, 0),
                BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            };

            constexpr auto calls = std::to_array({
                __NR_openat,
                __NR_read,
                __NR_pread64,
                __NR_close,
                __NR_readlinkat,
                __NR_fstat,
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
                instructions.push_back(BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, static_cast<uint32_t>(number), 0, 1));
                instructions.push_back(BPF_JUMP(BPF_JMP | BPF_JA, 0, 0, 0));
            }

            instructions.push_back(BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW));

            const size_t range_check = instructions.size();
            // size_t is wider than the 32-bit jump offset on 64-bit ABIs.
            for (size_t index = 5; index < range_check - 1; index += 2)
                // ReSharper disable once CppRedundantCastExpression
                element_at(instructions, index).k = static_cast<uint32_t>(range_check - index - 1);

            instructions.append_range(library_range);
            return instructions;
        }

        if (preserve_identity) {
            // Allow other architectures. Legacy syscall checks extend only this dispatch block;
            // all jumps inside the shared address/replay checks below remain unchanged.
            return {
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 0, 21 + legacy_open_count + metadata_count),
                // Route opens to their address check and descriptor metadata to its replay check.
                BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
                BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_openat, 3 + legacy_open_count + metadata_count, 0),
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
            };
        }

        // Traps file opens from the library's range unless they carry the replay marker.
        std::vector<sock_filter> instructions = {
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
        };
        instructions.append_range(library_range);
        return instructions;
    }

    // Pairip 11.6 forks through libc's syscall(), so the caller's address is not checked. The library
    // is the first code to run after this filter, so its raw fork comes first.
    std::vector<sock_filter> build_fork_filter() {
        return {
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, arch)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, syscall_arch, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, nr)),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, __NR_clone, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            // Allow the handler's replayed fork, marked in clone's unused sixth argument.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[5])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, replay_marker, 0, 1),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            // The handler forks for the caller and returns in both processes, which is only sound
            // when the child gets its own copy of memory and keeps the caller's stack. Bionic's fork()
            // always sets the child TID flags; crash reporters call it inside signal handlers that
            // block SIGSYS, where a trap would kill the process. Pairip forks with a raw clone.
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[0])),
            BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K,
                     CLONE_THREAD | CLONE_VM | CLONE_VFORK | CLONE_CHILD_SETTID | CLONE_CHILD_CLEARTID, 0, 1),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[1])),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(seccomp_data, args[1]) + 4),
            BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, 0, 1, 0),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
            BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_TRAP | trap_tag),
        };
    }

    // Uses raw syscalls only, so the signal handler can install a prepared program.
    std::expected<void, filter_failure> install_program(const std::span<sock_filter> instructions) {
        const sock_fprog program{
            .len = static_cast<unsigned short>(instructions.size()),
            .filter = instructions.data(),
        };

        if (const long result = signature_raw_syscall(__NR_prctl, PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0, 0); result != 0)
            return std::unexpected(filter_failure{.cause = "errno", .value = -result});

        // Never fall back to a filter that silently covers only the installer thread.
        const long result = signature_raw_syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC,
                                                  reinterpret_cast<long>(&program), 0, 0, 0);

        if (result < 0) return std::unexpected(filter_failure{.cause = "errno", .value = -result});
        if (result > 0)
            return std::unexpected(filter_failure{.cause = "unsynchronized thread", .value = result});

        return {};
    }

    // Fails with an errno, or with the ID of a thread that could not synchronize.
    std::expected<void, filter_failure> install_filter(const uintptr_t start, const uintptr_t end,
                                                       const bool preserve_identity,
                                                       const bool redirect_contents) {
        auto instructions = build_filter(start, end, preserve_identity, redirect_contents);
        return install_program(instructions);
    }

} // namespace signature::redirect
