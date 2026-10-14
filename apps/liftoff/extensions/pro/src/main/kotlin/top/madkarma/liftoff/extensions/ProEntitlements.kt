// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.liftoff.extensions

import app.reseam.runtime.Export
import java.text.SimpleDateFormat
import java.util.*

/** Supplies lifetime Pro entries in RevenueCat's React Native mapper format. */
object ProEntitlements {
    @Export
    @JvmStatic
    fun applyProEntitlements(result: MutableMap<String, Any?>) {
        val entries = proActiveEntries()
        result["active"] = entries
        result["all"] = entries
    }

    private fun proActiveEntries(): Map<String, Any?> {
        val nowMillis = System.currentTimeMillis()
        val nowIso = iso8601(nowMillis)

        val pro = hashMapOf<String, Any?>(
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

    private fun iso8601(millis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(millis))
    }
}
