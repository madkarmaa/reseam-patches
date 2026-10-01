// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.droplert.onboarding

// No method targets: the patch only writes the onboarding flag via appEntry,
// so there is nothing to fingerprint. Versions live here to mirror ads/ and
// premium/, keeping the patch file to patch code only.

val supportedVersions = setOf("2.2.1", "2.4.0", "2.4.1", "2.5.0", "2.5.1", "2.5.2")
