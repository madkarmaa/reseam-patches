// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SubscriptionUpsell") @file:Suppress("unused")

package top.madkarma.capcut.premium

import android.net.Uri
import android.util.Log
import app.reseam.runtime.Export
import org.json.JSONException
import org.json.JSONObject

private val subscriptionOffers = arrayOf(
    "profile_text_link",
    "profile_vip_text_link",
    "profile_fes_activity_banner_new",
)

@Export
fun removeSubscriptionOffers(payload: JSONObject?) {
    if (payload == null) return

    removeProfileOffers(payload)

    // app.fetch wraps its HTTP response body in a string; prefetch returns the resource map directly.
    val response = payload.opt("response") as? String ?: return
    if (subscriptionOffers.none { response.contains(it) }) return

    try {
        val body = JSONObject(response)
        if (removeProfileOffers(body)) payload.put("response", body.toString())
    } catch (exception: JSONException) {
        Log.w("CapCutPremium", "Could not read profile subscription offers", exception)
    }
}

private fun removeProfileOffers(payload: JSONObject): Boolean {
    var changed = removeOfferKeys(payload)

    payload.optJSONObject("resource_info")?.let { changed = removeOfferKeys(it) || changed }
    payload.optJSONObject("data")?.optJSONObject("resource_info")?.let {
        changed = removeOfferKeys(it) || changed
    }

    return changed
}

private fun removeOfferKeys(resources: JSONObject): Boolean {
    var changed = false
    for (key in subscriptionOffers) {
        if (!resources.has(key)) continue

        resources.remove(key)
        changed = true
        Log.i("CapCutPremium", "Removed subscription offer $key")
    }
    return changed
}

@Suppress("UseKtx")
@Export
fun isSubscriptionUpsell(url: String?): Boolean {
    if (url.isNullOrEmpty()) return false

    val uri = Uri.parse(url)
    if (!uri.isHierarchical || uri.getQueryParameter("channel") != "image_lynx_subscription_modal") return false

    Log.i("CapCutPremium", "Suppressed the home-page subscription upsell")
    return true
}
