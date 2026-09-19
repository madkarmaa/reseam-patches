@file:Suppress("unused")

package top.madkarma.patches.droplert.premium

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

val customerInfoIsPremiumActive = method("customer premium active") {
    returns(Type.Boolean)
    params("com.revenuecat.purchases.CustomerInfo")
    calls(CustomerInfo_getEntitlements)
    calls(EntitlementInfos_get)
    calls(EntitlementInfo_isActive)
}

val unconfiguredFallback = method("RevenueCat unconfigured fallback") {
    paramCount(3)
    returns(Type.Object)
    strings(
        "RevenueCat network call failed, using cached status",
    )
}

val cachedStatusLoader = method("cached premium status loader") {
    paramCount(2)
    returns(Type.Object)
    strings("Failed to load cached premium status")
}

val unlockPremiumLegacy = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1", "2.4.0"))
    dependsOn(removePairip)

    execute {
        customerInfoIsPremiumActive.method.alwaysReturn(true)

        val premiumField = runUnlockPremium()

        if (!swapFreeToPremium(unconfiguredFallback, premiumField)) {
            error("Premium: unconfigured fallback writes no FREE state")
        }

        if (!swapFreeToPremium(cachedStatusLoader, premiumField)) {
            error("Premium: cached loader writes no FREE state")
        }
    }
}
