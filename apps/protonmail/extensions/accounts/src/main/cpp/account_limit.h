// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <cstdint>
#include <optional>
// ReSharper restore CppUnusedIncludeDirective

struct account_limit {
    uint32_t *instruction;
    int protection;
};

struct instruction_pattern {
    uint32_t expected;
    uint32_t wildcard_example;
};
static_assert(sizeof(instruction_pattern) == 2 * sizeof(uint32_t));

extern "C" {
    extern const instruction_pattern proton_account_limit_pattern[6];
    extern const uint32_t proton_account_limit_nop;
}

std::optional<account_limit> find_account_limit();
