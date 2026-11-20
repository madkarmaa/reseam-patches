// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("CustomerInfo")
@file:Suppress("unused")

package top.madkarma.typinghero.extensions

import app.reseam.runtime.Export
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject
import top.madkarma.shared.dates.iso8601

private const val LIFETIME_PRODUCT = "lifetime"
private const val PURCHASE_DATE = "2018-01-01T00:00:00Z"

/**
 * A RevenueCat backend customer response with a one-time Play Store purchase that grants
 * `entitlement` for life.
 */
@Export
fun lifetimeCustomerInfo(entitlement: String?): JSONObject {
    val purchase =
        JSONObject()
            .put("id", LIFETIME_PRODUCT)
            .put("is_sandbox", false)
            .put("original_purchase_date", PURCHASE_DATE)
            .put("purchase_date", PURCHASE_DATE)
            .put("store", "play_store")

    val grant =
        JSONObject().put("product_identifier", LIFETIME_PRODUCT).put("purchase_date", PURCHASE_DATE)

    val subscriber =
        JSONObject()
            .put("first_seen", PURCHASE_DATE)
            .put("original_app_user_id", "\$RCAnonymousID:offline")
            .put("original_purchase_date", PURCHASE_DATE)
            .put("subscriptions", JSONObject())
            .put("non_subscriptions", JSONObject().put(LIFETIME_PRODUCT, JSONArray().put(purchase)))
            .put("entitlements", JSONObject().put(entitlement, grant))

    return JSONObject().put("request_date", iso8601(Date())).put("subscriber", subscriber)
}
