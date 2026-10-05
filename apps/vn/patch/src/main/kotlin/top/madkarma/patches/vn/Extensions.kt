// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vn

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object Prefs : ExtClass("top.madkarma.vn.extensions.Prefs") {
    val putBoolean by static(Type.Context, Type.String, Type.String, Type.Boolean)
}

internal object Pro : ExtClass("top.madkarma.vn.extensions.Pro") {
    val initializePreferences by static(Type.Context)

    val normalizeStartupConfiguration by static(Type.Object)

    const val PERSONALIZATION_ACTIVITY =
        $$"top.madkarma.vn.extensions.Pro$SkippedPersonalizationActivity"

    const val SUBSCRIPTION_ACTIVITY = $$"top.madkarma.vn.extensions.Pro$SkippedSubscriptionActivity"
}
