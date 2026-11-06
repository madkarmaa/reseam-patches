// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Preferences")
@file:Suppress("unused")

package top.madkarma.respiro.preferences

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export

@Export
fun premiumValue(key: Any?, value: Any?): Any? =
    when (key?.toString()) {
        "premium_unlocked",
        "widgets_unlocked" -> true
        else -> value
    }

@Export
fun introValue(key: Any?, value: Any?): Any? =
    when (key?.toString()) {
        "onboarding_intro_done" -> true
        "news_seen" -> Int.MAX_VALUE
        else -> value
    }

@Suppress("UseKtx")
@Export
fun skipSplash(context: Context) {
    context
        .getSharedPreferences("respiro_boot", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("splash_seen", true)
        .apply()
    Log.i("RespiroIntro", "First-launch splash marked as seen.")
}
