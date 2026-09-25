@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.invoke
import top.madkarma.patches.universal.removePairip
import top.madkarma.revisions.recordedPatch

val unlockPremium = recordedPatch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        val version = manifest.versionName

        if (version in premiumLegacyVersions) {
            customerInfoIsPremiumActive.method.alwaysReturn(true)
        }

        val premiumField = runUnlockPremium()

        if (version in premiumLegacyVersions) {
            if (!swapFreeToPremium(unconfiguredFallback, premiumField)) {
                error("Premium: unconfigured fallback writes no FREE state")
            }
            if (!swapFreeToPremium(cachedStatusLoader, premiumField)) {
                error("Premium: cached loader writes no FREE state")
            }
        }

        if (version in premiumSuspendVersions) {
            if (!swapFreeToPremium(unconfiguredFallbackSuspend, premiumField)) {
                error("Premium: unconfigured fallback writes no FREE state")
            }
            if (!swapFreeToPremium(cachedStatusLoaderSuspend, premiumField)) {
                error("Premium: cached loader writes no FREE state")
            }
        }

        if (version in tamperVersions) {
            tamperCheck.method.alwaysReturn(false)
        }
    }
}
