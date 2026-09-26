package top.madkarma.patches.droplert.ads

import app.reseam.patch.*

// Interstitial, rewarded, and the native composable kept their shapes
// across the coroutine refactor, so every disable-ads variant shares
// these targets. Loader variants below are applied by availability:
// whichever shapes exist in the app get patched.

val adsLegacyVersions = setOf("2.2.1", "2.4.0", "2.4.1")
val adsSuspendVersions = setOf("2.5.0", "2.5.1")
val supportedVersions = adsLegacyVersions + adsSuspendVersions

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

val nativeAdLoader = method("native ad loader") {
    paramCount(3)
    strings("Failed to build AdLoader.")
}

val nativeAdPreload = method("native ad preload") {
    returns(Type.Void)
    calls(nativeAdLoader)
}

// The coroutine refactor moved native ad loading into a suspend function
// (continuation param, Object return), so the direct 3-param loader form
// is gone there. The loader queries match only their own shape, so
// existence selects the right variant.

// The only AdLoader-string method returning Object here is the coroutine
// body (the dispatcher and SDK paths return Void), so the return type
// selects it alone.
val nativeAdLoaderSuspend = method("native ad loader (suspend)") {
    returns(Type.Object)
    strings("Failed to build AdLoader.")
}

// The ad-manager class is the first param of the native composable;
// its Void(String) entry points launch native loads.
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

// Shared body of the disable-ads patch: version-independent loaders.
fun PatchRuntime.runDisableAdsCommon() {
    interstitialLoader.method.alwaysReturn()
    rewardedAdLoader.method.alwaysReturn()
    nativeAdUi.method.alwaysReturn()
}
