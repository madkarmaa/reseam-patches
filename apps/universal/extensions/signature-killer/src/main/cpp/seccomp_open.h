// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <span>

struct raw_open_mapping {
    const char *installed;
    const char *replacement;
};

// Traps raw openat syscalls issued from the library's code and opens the replacement of any
// installed path instead. Strings must remain valid until process exit. Only one library can be
// configured per process. Logs according to the diagnostics flag in logging.h.
[[nodiscard]] bool install_raw_open_redirect(const char *library,
                                             std::span<const raw_open_mapping> paths);
