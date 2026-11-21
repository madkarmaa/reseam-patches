// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.linkboy.premium

import app.reseam.patch.*
import top.madkarma.linkboy.extensions.bindings.PremiumFeatures
import top.madkarma.patches.linkboy.LinkboyPatchStrings

val unlockPremium =
    patch(LinkboyPatchStrings.unlockPremiumName) {
        description(LinkboyPatchStrings.unlockPremiumDescription)
        compatibleWith("com.pixelrespawn.linkboy")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // The purchase-state flow and every premium check compare against these constants.
            initializePurchaseStates.after {
                lockedStates.forEach { setStatic(it, staticField(purchasedState)) }
            }
            log.info(
                "Premium: ${initializePurchaseStates.descriptor} resolves every purchase state to PURCHASED.",
            )

            stringArrayResource.after {
                returnValue(
                    PremiumFeatures.withAttribution(
                        param(0).field(resourceKey),
                        result,
                    ),
                )
            }
            log.info(
                "Premium: ${stringArrayResource.descriptor} adds the patch attribution to the premium feature list.",
            )
        }
    }
