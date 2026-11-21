// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.geotracker.pro

import app.reseam.patch.*
import top.madkarma.patches.geotracker.GeoTrackerPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro =
    patch(GeoTrackerPatchStrings.unlockProName) {
        description(GeoTrackerPatchStrings.unlockProDescription)
        compatibleWith("com.ilyabogdanovich.geotracker")

        execute {
            // Every refresh stores what the fetch returns, so the repository only ever holds Pro.
            fetchSubscription.replace { returnValue(proSubscription()) }
            log.info(
                "Pro: ${fetchSubscription.descriptor} returns an auto-renewing Pro subscription.",
            )

            // The Pro benefits end with a thank-you to subscribers.
            if (
                !resources.setStringEverywhere(
                    "subscription_benefit_gratitude_description",
                    PATCH_ATTRIBUTION,
                )
            )
                error("Pro: subscription_benefit_gratitude_description string missing")
            log.info(
                "Pro: subscription_benefit_gratitude_description shows the shared patch attribution.",
            )
        }
    }

private fun CodeScope.proSubscription(): ValueRef =
    newInstance(
        loadedSubscriptionConstructor.owner,
        loadedSubscriptionConstructor.proto,
        newInstance(
            paidTierStateConstructor.owner,
            paidTierStateConstructor.proto,
            staticField(proTier),
            bool(true), // Auto-renews, so the app offers no renewal.
            nullObject, // Expiration date.
            nullObject, // Management URL.
            nullObject, // Paid price.
            bool(false), // Trial.
            nullObject, // Expires at.
            nullObject, // Period started at.
        ),
        callStatic("java.util.Collections", "emptyMap", "()Ljava/util/Map;"), // Purchasable plans.
        nullObject, // Expired on.
    )
