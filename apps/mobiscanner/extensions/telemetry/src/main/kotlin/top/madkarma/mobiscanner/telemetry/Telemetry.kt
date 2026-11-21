// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Telemetry")
@file:Suppress("unused")

package top.madkarma.mobiscanner.telemetry

import app.reseam.runtime.Export

/** Whether a Remote Config key is the switch that starts Amplitude, Clarity or CleverTap. */
@Export
fun isTrackerSwitch(key: String?): Boolean =
    when (key) {
        "amplitude_sdk_enabled",
        "clarity_sdk_enabled",
        "clever_tap_sdk" -> true
        else -> false
    }
