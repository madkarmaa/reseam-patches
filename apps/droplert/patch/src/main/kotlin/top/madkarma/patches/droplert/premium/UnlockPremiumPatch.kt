// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.*
import top.madkarma.patches.droplert.Prefs
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.isPresent

val unlockPremium = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith(
        "com.shahzaman.pricetracker"(
            "2.2.1", "2.4.0", "2.4.1", "2.5.0", "2.5.1", "2.5.2", "2.5.3"
        )
    )
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        isPremium.alwaysReturn(true)
        entitlementIsActive.alwaysReturn(true)
        revenueCatStateUpdater.promoteFreeTier()
        playPurchaseCallback.promoteFreeTier()

        if (premiumCardStatus.replaceAllStrings(
                "All features unlocked", PATCH_ATTRIBUTION
            ) == 0
        ) error("Premium: settings card status text not found")

        appEntry {
            call(Prefs.putBoolean, application, string("has_seen_paywall"), bool(true))
        }

        val hasDirectCheck = isPresent(customerInfoIsPremiumActive)
        if (hasDirectCheck) customerInfoIsPremiumActive.alwaysReturn(true)

        val cachedWriters = unconfiguredFallbacks.all + cachedStatusLoaders.all
        if (!hasDirectCheck && cachedWriters.isEmpty()) error("Premium: unsupported app version")
        cachedWriters.forEach { it.promoteFreeTier() }

        if (isPresent(tamperCheck)) tamperCheck.alwaysReturn(false)
    }
}

// Keep each updater's original side effects while replacing the FREE values it consumes.
private fun MethodTarget.promoteFreeTier() {
    val freeReads = points("freeTierReads") {
        field {
            owner(premium.owner)
            name("FREE")
        }
    }.all
    if (freeReads.isEmpty()) error("Premium: $descriptor reads no FREE tier")

    freeReads.forEach {
        it.captureAs("tier").after { capture("tier").assign(staticField(premium)) }
    }
}
