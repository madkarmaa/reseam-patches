// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.lightroom.premium

import app.reseam.patch.PatchRuntime
import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.lightroom.LightroomPatchStrings
import top.madkarma.patches.lightroom.shared.lightroomIntegrity
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.shared.setStringEverywhere

// The Premium Features plan label and the sidebar's subscription status line.
private val ATTRIBUTION_STRINGS = listOf("premium_features_your_premium_plan", "youresubscribed")

val unlockPremium =
    patch(LightroomPatchStrings.unlockPremiumName) {
        description(LightroomPatchStrings.unlockPremiumDescription)
        compatibleWith("com.adobe.lrmobile")
        dependsOn(lightroomIntegrity)

        execute {
            for (name in ATTRIBUTION_STRINGS) {
                if (!resources.setStringEverywhere(name, PATCH_ATTRIBUTION))
                    error("Premium: missing string $name")
                log.info("Premium: string/$name displays the shared patch attribution.")
            }

            if (isPresent(lightroomMobileEntitlement)) unlockEntitlementGetter()
            else if (isPresent(storedEntitlementCheck)) unlockEntitlementField()
            else error("Premium: unsupported Lightroom entitlement holder")

            // Without cloud storage, the status has no line for the unlocked account.
            drawerSubscriptionStatus.after {
                whenNull(result) { returnValue(staticField(subscribedStatus)) }
            }
            log.info(
                "Premium: ${drawerSubscriptionStatus.descriptor} falls back to ${subscribedStatus.owner}->${subscribedStatus.name}, so the sidebar shows the subscription line.",
            )

            premiumFeaturesSheetPremium.alwaysReturn(true)
            log.info(
                "Premium: ${premiumFeaturesSheetPremium.descriptor} reports a premium user, so Premium Features shows the plan without trial buttons.",
            )

            firstLaunchUpsellEligible.alwaysReturn(false)
            log.info(
                "Premium: ${firstLaunchUpsellEligible.descriptor} rejects the first launch after sign-in, so the trial paywall no longer opens.",
            )
        }
    }

private fun PatchRuntime.unlockEntitlementGetter() {
    lightroomMobileEntitlement.alwaysReturn(true)
    log.info(
        "Premium: ${lightroomMobileEntitlement.descriptor} reports the Lightroom mobile entitlement, so premium tools unlock and the account status resolves to Subscription.",
    )
}

private fun PatchRuntime.unlockEntitlementField() {
    val flag = lightroomMobileFlag
    val initializer = entitlements.method("<clinit>")
    val writer =
        method("lightroomMobileFlagWriter") {
            inClass(entitlements)
            params(Type.Boolean)
            accesses { name(flag.name) }
        }

    initializer.after { setStatic(flag, bool(true)) }
    writer.alwaysReturn()
    log.info(
        "Premium: ${initializer.descriptor} sets ${flag.owner}->${flag.name} and ${writer.descriptor} no longer clears it, so premium tools unlock and the account status resolves to Subscription.",
    )
}
