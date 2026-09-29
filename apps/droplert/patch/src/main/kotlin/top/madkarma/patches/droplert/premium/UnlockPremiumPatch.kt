@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.universal.pairip.removePairip

val unlockPremium = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        val premiumField = runUnlockPremium()

        var patchedVariants = 0

        if (isPresent(customerInfoIsPremiumActive)) {
            customerInfoIsPremiumActive.method.alwaysReturn(true)
            patchedVariants++
            log.info("Premium: direct premium check patched.")
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
            log.info("Premium: patched ${fallbacks.size} fallback(s) and ${loaders.size} loader(s).")
        }

        if (isPresent(tamperCheck)) {
            tamperCheck.method.alwaysReturn(false)
            log.info("Premium: tamper check patched.")
        }

        if (patchedVariants == 0) error("Premium: unsupported app version")
    }
}
