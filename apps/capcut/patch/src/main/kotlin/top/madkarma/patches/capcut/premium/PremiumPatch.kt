// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.premium

import app.reseam.patch.*
import top.madkarma.capcut.premium.bindings.Profile
import top.madkarma.capcut.premium.bindings.SubscriptionUpsell
import top.madkarma.patches.capcut.integrity.bypassIntegrityChecks
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPremium = patch("Unlock Premium") {
    description("Unlocks Premium-only features and removes subscription offers. Most, if not all, AI tools run on CapCut's servers and cannot be unlocked by this patch.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(bypassIntegrityChecks)

    execute {
        premiumChecks.forEach { target ->
            target.alwaysReturn(true)
            log.info("Premium: ${target.descriptor} reports an active membership.")
        }

        accountMembershipLevel.replace { returnValue(staticField(highestMembership)) }
        log.info("Premium: ${accountMembershipLevel.descriptor} reports the VIP4 account tier.")

        homeSubscriptionButton.before { paramOfType(Type.Boolean).assign(bool(false)) }
        log.info("Premium: ${homeSubscriptionButton.descriptor} hides the home subscription and trial button.")

        // Profile campaigns arrive independently of native membership, through fetch or prefetch callbacks.
        lynxSuccessResponse.before {
            Profile.hideSubscriptionEntrance(paramOfType("org.json.JSONObject"))
            SubscriptionUpsell.removeSubscriptionOffers(paramOfType("org.json.JSONObject"))
        }
        log.info("Premium: ${lynxSuccessResponse.descriptor} removes the profile subscription chip, banners and trial cards.")

        lynxExperimentConfiguration.after {
            returnValue(Profile.subscriptionTouchConfiguration(paramOfType(Type.String), result))
        }
        log.info("Premium: ${lynxExperimentConfiguration.descriptor} keeps subscription touch targets from overlapping profile sign-in.")

        updateLynxTranslations.before {
            // The callback receives the resource channel, then the translation JSON, then its status.
            param(1).assign(Profile.withAttribution(param(1), string(PATCH_ATTRIBUTION)))
        }
        log.info("Premium: ${updateLynxTranslations.descriptor} displays the shared attribution in the profile Help Center row.")

        // The app resolves watermark benefits separately from account membership and checks trials first.
        watermarkBenefit.replace {
            val unmetered = callStatic("java.lang.Long", "valueOf", "(J)Ljava/lang/Long;", long(0))
            returnValue(
                newInstance(
                    "kotlin.Pair", "(Ljava/lang/Object;Ljava/lang/Object;)V",
                    staticField(premiumWatermarkBenefit), unmetered,
                )
            )
        }
        log.info("Premium: ${watermarkBenefit.descriptor} uses the unmetered VIP watermark benefit without consuming trials.")

        // Downloaded home-page JavaScript opens this upsell without checking native membership.
        openLynxView.before {
            whenTrue(SubscriptionUpsell.isSubscriptionUpsell(paramOfType(Type.String))) { returnVoid() }
        }
        log.info("Premium: ${openLynxView.descriptor} suppresses the home-page subscription upsell.")
    }
}
