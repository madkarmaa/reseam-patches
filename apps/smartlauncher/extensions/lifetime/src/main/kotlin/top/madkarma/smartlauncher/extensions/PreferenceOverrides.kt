// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("PreferenceOverrides") @file:Suppress("unused")

package top.madkarma.smartlauncher.extensions

import app.reseam.runtime.Export

// Null leaves the original preference behavior in place.
@Export
fun enabledOverride(preferenceName: String?, enabledPreference: String): Any? =
    if (preferenceName == enabledPreference) true else null
