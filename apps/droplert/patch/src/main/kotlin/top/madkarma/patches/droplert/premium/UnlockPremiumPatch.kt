// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.*
import top.madkarma.droplert.extensions.bindings.Prefs
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.isPresent

val unlockPremium = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith(
        "com.shahzaman.pricetracker"(
            "2.2.1", "2.4.0", "2.4.1", "2.5.0", "2.5.1", "2.5.2", "2.5.3", "2.5.4"
        )
    )
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        isPremium.alwaysReturn(true)
        log.info("Premium: ${isPremium.descriptor} enables premium features.")

        entitlementIsActive.alwaysReturn(true)
        log.info("Premium: ${entitlementIsActive.descriptor} reports an active entitlement.")

        revenueCatStateUpdater.promoteFreeTier()
        log.info("Premium: ${revenueCatStateUpdater.descriptor} stores the premium tier.")

        playPurchaseCallback.promoteFreeTier()
        log.info("Premium: ${playPurchaseCallback.descriptor} preserves the premium tier after purchase checks.")

        if (premiumCardStatus.method.replaceAllStrings(
                "All features unlocked", PATCH_ATTRIBUTION
            ) == 0
        ) error("Premium: settings card status text not found")
        log.info("Premium: ${premiumCardStatus.descriptor} displays the patch attribution.")

        appEntry {
            call(Prefs.putBoolean, application, string("has_seen_paywall"), bool(true))
        }

        val hasDirectCheck = isPresent(customerInfoIsPremiumActive)
        if (hasDirectCheck) {
            customerInfoIsPremiumActive.alwaysReturn(true)
            log.info("Premium: ${customerInfoIsPremiumActive.descriptor} reports an active premium account.")
        }

        val cachedWriters = unconfiguredFallbacks.all + cachedStatusLoaders.all
        if (!hasDirectCheck && cachedWriters.isEmpty()) error("Premium: unsupported app version")

        cachedWriters.forEach {
            it.promoteFreeTier()
            log.info("Premium: ${it.descriptor} preserves the premium tier in cached state.")
        }

        if (isPresent(tamperCheck)) {
            tamperCheck.alwaysReturn(false)
            log.info("Premium: ${tamperCheck.descriptor} reports no tampering.")
        }
    }
}

// Keep each updater's original side effects while replacing the FREE values it consumes.
private fun MethodTarget.promoteFreeTier() {
    val freeReads = freeTierReads()
    if (freeReads.isEmpty()) error("Premium: $descriptor reads no FREE tier")

    freeReads.forEach {
        it.captureAs("tier").after { capture("tier").assign(staticField(premium)) }
    }
}
