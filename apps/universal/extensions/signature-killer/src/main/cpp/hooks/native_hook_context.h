// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/seccomp_open.h"
#include <pthread.h>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    extern pthread_key_t stdio_caller_key;
    // Real code addresses cannot be these sentinels.
    inline auto *const internal_worker = reinterpret_cast<void *>(uintptr_t{1});
    inline auto *const foreign_stdio = reinterpret_cast<void *>(uintptr_t{2});
    void prepare_thread_context();

    // A target's stdio operation can enter read/open/lseek from inside libc. Keep that
    // operation's owner while libc handles its FILE buffer and validates its mode string.
    // Android 21's emulated C++ TLS allocates on first access, which would recurse through
    // the mmap hook while malloc is starting up. Bionic's pthread key lookup does not allocate.
    inline uintptr_t effective_caller(const uintptr_t direct) {
        const void *const context = pthread_getspecific(stdio_caller_key);
        if (context == internal_worker || context == foreign_stdio) return 0;
        if (context != nullptr) return reinterpret_cast<uintptr_t>(context);
        return direct;
    }

} // namespace signature::hooks
