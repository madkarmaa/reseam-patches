// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ProEntitlements")
@file:Suppress("unused")

package top.madkarma.liftoff.extensions

import app.reseam.runtime.Export
import java.util.Date
import top.madkarma.shared.dates.iso8601

/** Supplies lifetime Pro entries in RevenueCat's React Native mapper format. */
@Export
fun applyProEntitlements(result: MutableMap<String, Any?>) {
    val entries = proActiveEntries()
    result["active"] = entries
    result["all"] = entries
}

private fun proActiveEntries(): Map<String, Any?> {
    val nowMillis = System.currentTimeMillis()
    val nowIso = iso8601(Date(nowMillis))

    val pro =
        hashMapOf<String, Any?>(
            "identifier" to "pro",
            "isActive" to true,
            "willRenew" to false,
            "periodType" to "NORMAL",
            "latestPurchaseDateMillis" to nowMillis,
            "latestPurchaseDate" to nowIso,
            "originalPurchaseDateMillis" to nowMillis,
            "originalPurchaseDate" to nowIso,
            "expirationDateMillis" to null,
            "expirationDate" to null,
            "store" to "PLAY_STORE",
            "productIdentifier" to "pro",
            "productPlanIdentifier" to null,
            "isSandbox" to false,
            "unsubscribeDetectedAt" to null,
            "unsubscribeDetectedAtMillis" to null,
            "billingIssueDetectedAt" to null,
            "billingIssueDetectedAtMillis" to null,
            "ownershipType" to "PURCHASED",
            "verification" to "VERIFIED",
        )

    return hashMapOf("pro" to pro, "premium" to pro)
}
