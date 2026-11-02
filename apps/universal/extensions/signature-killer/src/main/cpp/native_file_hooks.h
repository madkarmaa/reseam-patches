// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// Covers libc calls and executable mappings made by the protected native library.
[[nodiscard]] bool prepare_native_file_hooks();

// Recheck forwarding entries after a protector finishes rewriting libc.
void refresh_native_file_hooks();

// Snapshot workers must read the real procfs input, even through another library's libc hooks.
void bypass_native_file_hooks_for_thread();
[[nodiscard]] bool native_file_hooks_bypassed();
