@file:Suppress("unused")

package top.madkarma.patches.droplert

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

val interstitialLoader = method("interstitial ad loader") {
    strings("Loading on UI thread")
}

val rewardedAdLoader = method("rewarded ad scenario loader") {
    paramCount(1)
    calls(interstitialLoader)
}

val nativeAdLoader = method("native ad loader") {
    paramCount(3)
    strings("Failed to build AdLoader.")
}

val nativeAdPreload = method("native ad preload") {
    returns(Type.Void)
    calls(nativeAdLoader)
}

val nativeAdUi = method("native ad composable") {
    paramCount(10)
    callsMethod { definingClass == "Lcom/google/android/gms/ads/nativead/NativeAd;" }
}

val disableAds = patch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1", "2.4.0", "2.4.1"))
    dependsOn(removePairip)

    execute {
        interstitialLoader.method.alwaysReturn()
        rewardedAdLoader.method.alwaysReturn()
        nativeAdLoader.method.alwaysReturn()
        nativeAdPreload.method.alwaysReturn()
        nativeAdUi.method.alwaysReturn()
        log.info("Ads: neutralized native, rewarded, and interstitial entry points.")
    }
}
