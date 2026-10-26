// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <memory>
#include <string>
#include <vector>

struct path_mapping {
    // Android's installed base or split path.
    std::string installed;
    // The extracted original file for that component.
    std::string replacement;
};

using path_mappings = std::vector<path_mapping>;

// The table installed by install_file_redirect, or null before installation.
const path_mappings *installed_path_mappings();

// Installs the process's only mapping table, which is never freed, then hooks the libc file opens
// of every loaded and later library. The hooks stay registered for the process lifetime.
[[nodiscard]] bool install_file_redirect(std::unique_ptr<path_mappings> table);
