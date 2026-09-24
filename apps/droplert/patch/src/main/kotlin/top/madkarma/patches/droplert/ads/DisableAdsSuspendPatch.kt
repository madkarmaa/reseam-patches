@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.*
import top.madkarma.patches.universal.removePairip
import top.madkarma.revisions.recordedPatch

// 2.5.0 moved native ad loading into a coroutine (continuation param,
// Object return), so the direct 3-param loader form is gone there.

// The only AdLoader-string method returning Object here is the coroutine
// body (the dispatcher and SDK paths return Void).
val nativeAdLoaderSuspend = method("native ad loader (suspend)") {
    paramCount(1)
    returns(Type.Object)
    strings("Failed to build AdLoader.")
}

// The ad-manager class is the first param of the native composable
// (u4 in 2.5.0); its Void(String) entry points launch native loads.
val adManagerClassSuspend = classTarget("ad manager class (suspend)") {
    val mgr =
        nativeAdUi.method.parameterTypes.firstOrNull() ?: error("Ads: native UI has no params")
    bytecode.findClass(mgr) ?: error("Ads: ad manager class not found: $mgr")
}

val nativeAdPreloadsSuspend = adManagerClassSuspend.methods("native ad preloads (suspend)") {
    returns(Type.Void)
    paramCount(1)
    param(0, Type.String)
}

val disableAdsSuspend = recordedPatch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"("2.5.0", "2.5.1"))
    dependsOn(removePairip)

    execute {
        runDisableAdsCommon()
        nativeAdLoaderSuspend.method.alwaysReturnNull()
        nativeAdPreloadsSuspend.forEach { method.alwaysReturn() }
        log.info("Ads: neutralized native, rewarded, and interstitial entry points.")

    }
}
