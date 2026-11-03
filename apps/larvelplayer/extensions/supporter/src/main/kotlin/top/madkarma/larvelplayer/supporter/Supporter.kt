// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Supporter") @file:Suppress("unused")

package top.madkarma.larvelplayer.supporter

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export

@Suppress("UseKtx")
@Export
fun unlock(context: Context) {
    context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE).edit()
        .putBoolean("is_supporter", true).putBoolean("ads_removed", true).apply()
    Log.i("LarvelSupporter", "Lifetime supporter access enabled")
}
