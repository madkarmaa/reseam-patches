// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <android/log.h>

#include <atomic>

// Set from the patch's diagnostics option right after loading. When off, nothing is logged.
inline constinit std::atomic<bool> diagnostics{false};

inline bool diagnostics_enabled() { return diagnostics.load(std::memory_order_relaxed); }

#define LOG(priority, ...)                                                                    \
    do {                                                                                      \
        if (diagnostics_enabled())                                                            \
            __android_log_print(priority, "SignatureKiller", __VA_ARGS__);                    \
    } while (false)

#define LOG_DEBUG(...) LOG(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define LOG_INFO(...) LOG(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOG_WARN(...) LOG(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOG_ERROR(...) LOG(ANDROID_LOG_ERROR, __VA_ARGS__)
