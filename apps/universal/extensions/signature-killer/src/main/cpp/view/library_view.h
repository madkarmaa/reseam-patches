// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/seccomp_open.h"
// ReSharper restore CppUnusedIncludeDirective

struct library_view_options {
    bool (*contains_address)(uintptr_t) = nullptr;
    unsigned (*filter_count)() = nullptr;
    void (*enter_worker)() = nullptr;
};

// Excludes the extension's support libraries from the target's loaded-module queries.
[[nodiscard]] bool prepare_library_view(const char *library, const library_view_options &options = {});

// Signal-safe request to the snapshot worker. Returns a descriptor or a negative errno.
[[nodiscard]] int snapshot_process_file(proc_file file, int requested_process = 0);
