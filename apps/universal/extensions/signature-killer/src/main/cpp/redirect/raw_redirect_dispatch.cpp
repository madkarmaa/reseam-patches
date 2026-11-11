// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <sys/syscall.h>
#include <array>
#include <algorithm>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
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
            (number == __NR_read || number == __NR_pread64 || number == __NR_close || is_mmap_syscall(number)
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

    long redirect_file_syscall(redirect_state &state, const int number, const syscall_arguments &arguments) {
        int mapping = -1;
        long result = 0;
        if (state.redirect_file_contents && !is_open_syscall(number) && !is_readlink_syscall(number) &&
            !is_fstat_syscall(number) && !is_fstatat_syscall(number))
            result = redirect_content_io(state, number, arguments, mapping);
        else if (state.preserve_descriptor_identity && !is_open_syscall(number))
            result = redirect_metadata(state, number, arguments, mapping);
        else result = redirect_open(state, number, arguments, mapping);

        report_event(state, number, result, mapping);
        return result;
    }

} // namespace signature::redirect
