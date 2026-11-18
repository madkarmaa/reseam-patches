// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Preferences")
@file:Suppress("unused")

package top.madkarma.liveloop.preferences

import android.content.Context
import app.reseam.runtime.Export

private const val PREFERENCES_FILE = "liveloop_default"
private const val PREMIUM_KEY = "pro_status"

@Export fun premiumValue(key: String?, value: Boolean): Boolean = value || key == PREMIUM_KEY

@Suppress("UseKtx")
@Export
fun unlockPremium(context: Context) {
    context
        .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREMIUM_KEY, true)
        .apply()
}
