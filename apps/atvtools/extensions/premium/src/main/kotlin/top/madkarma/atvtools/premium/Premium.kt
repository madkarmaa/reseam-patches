// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Premium")
@file:Suppress("unused")

package top.madkarma.atvtools.premium

import android.app.Activity
import android.util.Log
import android.widget.Toast
import app.reseam.runtime.Export
import org.json.JSONObject

@Export
fun state(purchaseClassName: String): Any {
    val receipt =
        JSONObject()
            .put("packageName", "dev.vodik7.atvtools")
            .put("productId", "coffee_1")
            .put("purchaseToken", "reseam-premium")
            .put("purchaseTime", System.currentTimeMillis())
            .put("purchaseState", 0)
            .put("acknowledged", true)
            .put("autoRenewing", false)

    val purchase =
        Class.forName(purchaseClassName)
            .getConstructor(String::class.java, String::class.java)
            .newInstance(receipt.toString(), "")

    val state =
        Class.forName("kotlinx.coroutines.flow.StateFlowKt")
            .getMethod("MutableStateFlow", Any::class.java)
            .invoke(null, listOf(purchase))!!

    Log.i("ATVToolsPremium", "Permanent premium entitlement enabled")
    return state
}

@Export
fun showAttribution(activity: Activity, attribution: String) {
    Toast.makeText(activity, attribution, Toast.LENGTH_LONG).show()
    activity.finish()
}
