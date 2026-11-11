// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    struct resolved_hook {
        const char *name;
        uintptr_t address;
        void *proxy;
    };

    // Published once all libc entry hooks exist. mprotect may enter during their installation.
    extern std::vector<resolved_hook> resolved_hooks;
    extern std::atomic<bool> forwarders_ready;

    void intercept_libc_forwarders(const void *address, size_t size, int protection);
} // namespace signature::hooks
