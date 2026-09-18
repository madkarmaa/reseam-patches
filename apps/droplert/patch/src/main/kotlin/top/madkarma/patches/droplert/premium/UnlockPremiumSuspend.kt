package top.madkarma.patches.droplert.premium

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

// 2.4.1 turned the premium checks into suspend functions (continuation
// param, Object return), so the direct Boolean forms are gone there.

// 2.4.1 turned the fallback into a suspend function (flag + continuation).
val unconfiguredFallbackSuspend = method("RevenueCat unconfigured fallback (suspend)") {
    paramCount(2)
    param(0, Type.Boolean)
    returns(Type.Object)
    strings(
        "RevenueCat network call failed, using cached status",
    )
}

// 2.4.1 turned the loader into a suspend function (continuation only).
val cachedStatusLoaderSuspend = method("cached premium status loader (suspend)") {
    paramCount(1)
    returns(Type.Object)
    strings("Failed to load cached premium status")
}

val unlockPremiumSuspend = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"("2.4.1"))
    dependsOn(removePairip)

    execute {
        val premiumField = runUnlockPremium()

        if (!swapFreeToPremium(unconfiguredFallbackSuspend, premiumField)) {
            error("Premium: unconfigured fallback writes no FREE state")
        }

        if (!swapFreeToPremium(cachedStatusLoaderSuspend, premiumField)) {
            error("Premium: cached loader writes no FREE state")
        }
    }
}
