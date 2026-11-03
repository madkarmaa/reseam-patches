// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <cstdint>
#include <optional>
// ReSharper restore CppUnusedIncludeDirective

// Match at instruction boundaries without imposing ARM alignment on x86 code.
#if defined(__aarch64__)
using instruction_unit = uint32_t;
#elif defined(__arm__)
using instruction_unit = uint16_t;
#elif defined(__x86_64__)
using instruction_unit = uint8_t;
#else
#error Unsupported Proton Mail architecture
#endif

struct account_limit {
    uint8_t *instruction;
    int protection;
};

extern "C" {
    extern const instruction_unit proton_account_limit_pattern[];
    extern const instruction_unit proton_account_limit_wildcards[];
    extern const uint8_t proton_account_limit_replacement[];
    extern const uint32_t proton_account_limit_size;
    extern const uint32_t proton_account_limit_wildcards_size;
    extern const uint32_t proton_account_limit_patch_offset;
    extern const uint32_t proton_account_limit_replacement_size;
}

std::optional<account_limit> find_account_limit();
