// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.backgroundvideorecorder.premium

import app.reseam.patch.*
import top.madkarma.patches.backgroundvideorecorder.BackgroundVideoRecorderPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPremium =
    patch(BackgroundVideoRecorderPatchStrings.unlockPremiumName) {
        description(BackgroundVideoRecorderPatchStrings.unlockPremiumDescription)
        compatibleWith("com.helgeapps.backgroundvideorecorder")

        execute {
            // Every product reports as owned, so the donation and subscription screens show
            // premium.
            val constructors = purchaseInfoConstructors.all
            if (constructors.isEmpty()) error("Premium: no PurchaseInfo constructors found")
            constructors.forEach {
                it.before { paramOfType(Type.Boolean).assign(bool(true)) }
                log.info("Premium: ${it.descriptor} marks the product as purchased.")
            }

            // Readers observe the flow the constructor seeds with NO_ADS. Purchase updates write to
            // a
            // detached flow instead, so an empty or unowned product list never re-enables ads.
            billingManagerConstructor.after {
                thisObject.set(adModeUpdates, call(stateFlowFactory, staticField(noAds)))
            }
            log.info(
                "Premium: ${billingManagerConstructor.descriptor} detaches ${adModeUpdates.name} from the NO_ADS mode readers observe.",
            )

            // Billing failures during construction reset the observed flow before it is detached.
            forceAllAds.alwaysReturn()
            log.info("Premium: ${forceAllAds.descriptor} no longer re-enables ads.")

            if (
                !resources.setStringEverywhere(
                    "subscription_status_subscribed",
                    "You are subscribed.\n$PATCH_ATTRIBUTION",
                )
            )
                error("Premium: subscription_status_subscribed string missing")
            log.info("Premium: subscription_status_subscribed shows the shared patch attribution.")
        }
    }
