// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once
#include "seccomp_open.h"

// Excludes the extension's support libraries from one library's loaded-module queries.
[[nodiscard]] bool prepare_library_view(const char *library);

// Signal-safe request to the snapshot worker. Returns a descriptor or a negative errno.
[[nodiscard]] int snapshot_process_file(proc_file file);
