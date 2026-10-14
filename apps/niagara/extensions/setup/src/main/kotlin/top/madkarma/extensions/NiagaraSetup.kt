// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("NiagaraSetup") @file:Suppress("unused")

package top.madkarma.extensions

import android.content.Context
import app.reseam.runtime.Export

private const val PREFS = "bitpit.launcher_preferences"
private const val KEY_BLOCK = "bitpit.launcher.key.ONBOARDING_BLOCK"
private const val KEY_TERMS_VERSION = "bitpit.launcher.key.TERMS_ACCEPTED_VERSION"
private const val KEY_ANALYTICS_SELECTION = "bitpit.launcher.key.ONBOARDING_ANALYTICS_SELECTION"
private const val KEY_MARKETING_ANALYTICS_ENABLED =
    "bitpit.launcher.key.MARKETING_ANALYTICS_ENABLED"
private const val KEY_ANALYTICS = "bitpit.launcher.key.ANALYTICS"
private const val BLOCK_SELECT_FAVORITES = "select_favorites"
private const val TERMS_VERSION = 2

// Mirrors tapping Refuse.
private const val ANALYTICS_REFUSAL =
    """{"allowMarketingAnalytics":false,"allowFunctionalAnalytics":false}"""

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

@Suppress("UseKtx")
@Export
fun skipToFavoritesSetup(context: Context) {
    prefs(context).edit().putString(KEY_BLOCK, BLOCK_SELECT_FAVORITES).apply()
}

@Suppress("UseKtx")
@Export
fun acceptTerms(context: Context) {
    prefs(context).edit().putInt(KEY_TERMS_VERSION, TERMS_VERSION).apply()
}

@Suppress("UseKtx")
@Export
fun refuseAnalytics(context: Context) {
    prefs(context).edit().putString(KEY_ANALYTICS_SELECTION, ANALYTICS_REFUSAL).apply()
}

@Suppress("UseKtx")
@Export
fun disableAnalyticsFlags(context: Context) {
    prefs(context).edit().putBoolean(KEY_MARKETING_ANALYTICS_ENABLED, false)
        .putBoolean(KEY_ANALYTICS, false).apply()
}
