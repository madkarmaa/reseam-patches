// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <cstdint>
#include <span>

enum class proc_file { maps, status };

struct raw_redirect_options {
    uintptr_t start = 0;
    uintptr_t end = 0;
    // Masks replacement paths and ownership without changing real inodes or content metadata.
    bool preserve_descriptor_identity = false;
    // Keep installed descriptors and mappings while serving original bytes on reads.
    bool redirect_file_contents = false;
    // Optional signal-safe provider of a filtered procfs snapshot descriptor.
    int (*snapshot_proc_file)(proc_file) = nullptr;
};

struct raw_open_mapping {
    const char *installed;
    const char *replacement;
};

// Traps raw open/openat syscalls issued from the library's code and opens the replacement of any
// installed path instead. Descriptor mode also handles readlink/readlinkat, fstat/fstat64 and fstatat
// where the ABI provides them. Strings must remain valid until process exit. Only one library can be
// configured per process. Logs according to the diagnostics flag in logging.h.
[[nodiscard]] bool install_raw_open_redirect(const char *library,
                                             std::span<const raw_open_mapping> paths,
                                             const raw_redirect_options &options = {});

// Installs after dlopen completes and before Android calls the library's JNI_OnLoad.
[[nodiscard]] bool prepare_library_file_redirect(const char *library,
                                                std::span<const raw_open_mapping> paths,
                                                bool redirect_contents = false);
