// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("PremiumPreferences")
@file:Suppress("unused")

package top.madkarma.turuta.extensions

import android.content.SharedPreferences
import android.util.Log
import app.reseam.runtime.Export
import org.json.JSONObject

@Suppress("UseKtx")
@Export
fun enable(preferences: SharedPreferences) {
    check(
        preferences
            .edit()
            .putBoolean("flutter.has_active_premium_cache", true)
            .putBoolean("flutter.PK_show_ads", false)
            .commit(),
    ) {
        "Could not save TuRuta premium preferences"
    }
    Log.i("TuRutaPremium", "Premium preferences enabled before Flutter startup")
}

/** The Play Billing receipt of a lifetime pass, for the app's own Purchase class to parse. */
@Export
fun lifetimeReceipt(): String =
    JSONObject()
        .put("packageName", "pe.tumicro.android")
        .put("productId", "lifetime_pass_a")
        .put("purchaseToken", "reseam-premium")
        .put("purchaseTime", System.currentTimeMillis())
        .put("purchaseState", 0)
        .put("acknowledged", true)
        .put("autoRenewing", false)
        .toString()

/** [purchases] with [purchase] added. */
@Export
fun withPurchase(purchases: List<Any?>?, purchase: Any?): List<Any?> {
    val result = ArrayList(purchases.orEmpty())
    result.add(purchase)

    Log.i("TuRutaPremium", "Added lifetime Premium to Flutter purchase results")
    return result
}
