// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <android/log.h>
#include <cstdint>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

#define LOG_INFO(...) __android_log_print(ANDROID_LOG_INFO, "PowerampPlayback", __VA_ARGS__)
#define LOG_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, "PowerampPlayback", __VA_ARGS__)

// Poweramp's pipeline checks the native trial verdict when a pipeline is created and when it
// opens a track after a failed check.
inline constexpr unsigned expected_license_checks = 2;

struct license_check {
    uint32_t *instruction;
    int protection;
};

extern "C" {
    extern const uint32_t license_check_pattern[];
    extern const uint32_t license_check_wildcards[];
    extern const uint32_t license_check_keep;
    extern const uint32_t license_check_replacement;
    extern const uint32_t license_check_size;
    extern const uint32_t license_check_wildcards_size;
    extern const uint32_t license_check_patch_offset;
}

std::vector<license_check> find_license_checks();
