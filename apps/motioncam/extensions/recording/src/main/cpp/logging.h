// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <android/log.h>
#include <atomic>

inline constinit std::atomic<bool> diagnostics{false};

#define LOG(priority, ...)                                                    \
    do {                                                                      \
        if (diagnostics.load(std::memory_order_relaxed))                       \
            __android_log_print(priority, "MotionCamRecording", __VA_ARGS__); \
    } while (false)

#define LOG_INFO(...) LOG(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOG_ERROR(...) LOG(ANDROID_LOG_ERROR, __VA_ARGS__)
