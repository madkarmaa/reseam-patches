// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <cstdint>
#include <optional>
// ReSharper restore CppUnusedIncludeDirective

struct recording_limit {
    uint32_t *instruction;
    int protection;
};

extern "C" {
    extern const uint32_t motioncam_limit_pattern[];
    extern const uint32_t motioncam_limit_wildcards[];
    extern const uint32_t motioncam_limit_replacement;
    extern const uint32_t motioncam_limit_size;
    extern const uint32_t motioncam_limit_wildcards_size;
    extern const uint32_t motioncam_limit_patch_offset;
}

std::optional<recording_limit> find_recording_limit();
