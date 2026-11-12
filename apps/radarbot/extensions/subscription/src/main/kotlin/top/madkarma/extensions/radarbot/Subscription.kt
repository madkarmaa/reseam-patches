// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Subscription")
@file:Suppress("unused")

package top.madkarma.extensions.radarbot

import android.util.Log
import app.reseam.runtime.Export

/**
 * Marks [tier] and every lower tier as active. Tiers are ordered from lowest to highest, and each
 * flag in [tierFlags] belongs to the entitlement name at the same index in [tierNames].
 */
@Export
fun grant(tierNames: Array<String?>?, tierFlags: BooleanArray?, tier: String?) {
    val highestTier = tierNames?.indexOf(tier) ?: -1
    if (tierFlags == null || highestTier < 0 || highestTier >= tierFlags.size) {
        Log.w("ReseamRadarbot", "Subscription tier $tier is unavailable")
        return
    }

    for (index in 0..highestTier) tierFlags[index] = true
}
