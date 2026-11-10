// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.premium

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.ads.disableAds
import top.madkarma.truecaller.behavior.bindings.Premium

val unlockPremium =
    patch(TruecallerPatchStrings.unlockPremiumName) {
        description(
            TruecallerPatchStrings.unlockPremiumDescription,
        )
        compatibleWith("com.truecaller")
        dependsOn(disableAds)

        execute {
            check(
                resources.setStringEverywhere("PremiumNavDrawerSwitchToTier", PATCH_ATTRIBUTION),
            ) {
                "Premium profile subtitle resource is missing"
            }
            log.info("Premium: string/PremiumNavDrawerSwitchToTier shows the patch attribution.")

            isPremiumActive.alwaysReturn(true)
            log.info("Premium: ${isPremiumActive.descriptor} reports an active subscription.")

            premiumTierGetter.replace { returnValue(Premium.tier()) }
            log.info("Premium: ${premiumTierGetter.descriptor} returns the Premium tier.")

            featureOffered.alwaysReturn(true)
            log.info("Premium: ${featureOffered.descriptor} marks Premium features as available.")

            featureIncluded.alwaysReturn(true)
            log.info("Premium: ${featureIncluded.descriptor} allows access to Premium features.")
        }
    }
