// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.strava.subscription

import app.reseam.patch.alwaysReturn
import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.strava.StravaPatchStrings

val unlockSubscription =
    patch(StravaPatchStrings.unlockSubscriptionName) {
        description(StravaPatchStrings.unlockSubscriptionDescription)
        compatibleWith("com.strava")

        execute {
            detailIsPremium.alwaysReturn(true)
            log.info("Subscription: ${detailIsPremium.descriptor} forced true.")

            snapshotIsPremium.alwaysReturn(true)
            log.info("Subscription: ${snapshotIsPremium.descriptor} forced true.")

            createDataPeekUpsell.alwaysReturnNull()
            log.info(
                "Subscription: ${createDataPeekUpsell.descriptor} drops the locked feature preview and its trial button.",
            )

            val summaryName = "subscription_settings_preference_summary_v2"
            check(resources.setStringEverywhere(summaryName, PATCH_ATTRIBUTION)) {
                "Subscription row summary is missing"
            }
            log.info("Subscription: Settings' subscription row shows the shared patch attribution.")
        }
    }
