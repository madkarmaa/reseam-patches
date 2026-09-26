@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.invoke
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.universal.removePairip
import top.madkarma.revisions.recordedPatch

val unlockPremium = recordedPatch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        val premiumField = runUnlockPremium()

        var patchedVariants = 0

        if (isPresent(customerInfoIsPremiumActive)) {
            customerInfoIsPremiumActive.method.alwaysReturn(true)
            patchedVariants++
        }

        val fallbacks = unconfiguredFallbacks.all
        val loaders = cachedStatusLoaders.all

        if (fallbacks.isNotEmpty() || loaders.isNotEmpty()) {
            fallbacks.forEach {
                if (!swapFreeToPremium(
                        it, premiumField
                    )
                ) error("Premium: unconfigured fallback writes no FREE state (${it.descriptor})")
            }

            loaders.forEach {
                if (!swapFreeToPremium(
                        it, premiumField
                    )
                ) error("Premium: cached loader writes no FREE state (${it.descriptor})")
            }

            patchedVariants++
        }

        if (isPresent(tamperCheck)) {
            tamperCheck.method.alwaysReturn(false)
        }

        if (patchedVariants == 0) error("Premium: unsupported app version")
    }
}
