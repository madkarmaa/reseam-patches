// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include <cstdint>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
#ifdef __aarch64__
    struct forwarding_branch {
        uintptr_t destination = 0;
        uintptr_t literal = 0;
    };

    forwarding_branch forwarding_destination(uintptr_t entry);
    bool retarget_literal(uintptr_t literal, const void *proxy);
#endif
} // namespace signature::hooks
