// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.vn.extensions

import android.app.Activity
import android.content.Context
import android.os.Bundle
import app.reseam.runtime.Export
import com.frontrow.common.model.SplashAdvertisement

object Pro {
    @Export
    @JvmStatic
    fun initializePreferences(context: Context) {
        Prefs.putBoolean(context, "devices", "KEY_LAST_SHOW_PRO_FEATURE", false)
        Prefs.putBoolean(context, "debug", "ENABLE_SHOW_STORE", false)
        Prefs.putBoolean(context, "debug", "SHOW_AD_ALWAYS", false)
    }

    @Export
    @JvmStatic
    fun normalizeStartupConfiguration(response: Any?) {
        // R8 shares the startup Consumer with its Throwable error path.
        if (response !is SplashAdvertisement) return

        response.ShowStore = false
        response.ADJSON = "[]"
        response.GoogleADEnabled = 0
    }

    @Export
    class SkippedPersonalizationActivity : Activity() {
        override fun onCreate(state: Bundle?) {
            super.onCreate(state)
            finish()
        }
    }

    @Export
    class SkippedSubscriptionActivity : Activity() {
        override fun onCreate(state: Bundle?) {
            super.onCreate(state)
            setResult(RESULT_OK)
            finish()
        }
    }
}
