// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vn

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object Prefs : ExtClass("top.madkarma.vn.extensions.Prefs") {
    val putBoolean = static("putBoolean", Type.Context, Type.String, Type.String, Type.Boolean)
}

object Pro : ExtClass("top.madkarma.vn.extensions.Pro") {
    val initializePreferences = static("initializePreferences", Type.Context)

    val normalizeStartupConfiguration = static("normalizeStartupConfiguration", Type.Object)

    const val PERSONALIZATION_ACTIVITY =
        $$"top.madkarma.vn.extensions.Pro$SkippedPersonalizationActivity"

    const val SUBSCRIPTION_ACTIVITY = $$"top.madkarma.vn.extensions.Pro$SkippedSubscriptionActivity"
}
