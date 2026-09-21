package top.madkarma.patches.droplert.ads

import app.reseam.patch.PatchRuntime
import app.reseam.patch.method

// Interstitial, rewarded, and the native composable kept their shapes
// across the 2.5.0 coroutine refactor, so both disable-ads variants
// share these targets.

val interstitialLoader = method("interstitial ad loader") {
    strings("Loading on UI thread")
}

val rewardedAdLoader = method("rewarded ad scenario loader") {
    paramCount(1)
    calls(interstitialLoader)
}

val nativeAdUi = method("native ad composable") {
    paramCount(10)
    callsMethod { definingClass == "Lcom/google/android/gms/ads/nativead/NativeAd;" }
}

// Shared body of both disable-ads patches.
fun PatchRuntime.runDisableAdsCommon() {
    interstitialLoader.method.alwaysReturn()
    rewardedAdLoader.method.alwaysReturn()
    nativeAdUi.method.alwaysReturn()
}
