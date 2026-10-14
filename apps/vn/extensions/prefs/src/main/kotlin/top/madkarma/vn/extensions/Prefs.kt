// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.vn.extensions

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export

object Prefs {
    private const val TAG = "Prefs"

    @Export
    @JvmStatic
    fun putBoolean(context: Context, store: String, key: String, value: Boolean) {
        try {
            context.getSharedPreferences(store, Context.MODE_PRIVATE).edit().putBoolean(key, value)
                .apply()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not write $store.$key", exception)
        }
    }
}
