// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_file_hooks.h"
#include "hooks/native_hook_context.h"
#include <cerrno>
#include <span>
#include <type_traits>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

// Expand in the proxy, before any helper changes the stack frame.
#define NATIVE_CALLER reinterpret_cast<uintptr_t>(__builtin_return_address(0))

namespace signature::hooks {
    template <typename Result> Result libc_result(const long result, const int previous_errno) {
        const bool failed = result < 0 && result >= -4095;
        errno = failed ? static_cast<int>(-result) : previous_errno;
        if constexpr (std::is_pointer_v<Result>)
            return failed ? reinterpret_cast<Result>(-1) : reinterpret_cast<Result>(result);
        else return failed ? static_cast<Result>(-1) : static_cast<Result>(result);
    }

    inline long redirect(const uintptr_t caller, const int number, const long first, const long second = 0,
                         const long third = 0, const long fourth = 0, const long fifth = 0,
                         const long sixth = 0) {
        if (const auto result = redirect_native_file_syscall(effective_caller(caller), number, first, second,
                                                             third, fourth, fifth, sixth);
            result)
            return *result;

        // The protector can replace libc's prologue after our hook. A relocated trampoline
        // would then return into the middle of its new branch; replay the syscall instead.
        return signature_raw_syscall(number, first, second, third, fourth, fifth, sixth);
    }

    struct libc_hook {
        const char *name;
        void *proxy;
        void **original;
        bool intercept_forwarder;
    };

    template <typename Function>
    libc_hook hook(const char *const name, Function proxy, Function &original,
                   const bool intercept_forwarder = true) {
        return {
            .name = name,
            .proxy = reinterpret_cast<void *>(proxy),
            .original = reinterpret_cast<void **>(&original),
            .intercept_forwarder = intercept_forwarder,
        };
    }

    std::span<const libc_hook> memory_hooks();
    std::span<const libc_hook> file_io_hooks();
    std::span<const libc_hook> file_metadata_hooks();
    std::span<const libc_hook> stdio_hooks();
    void prepare_file_io_hooks(void *libc);
} // namespace signature::hooks
