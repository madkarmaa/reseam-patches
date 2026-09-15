package top.madkarma.patches.droplert

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

// AdMob entry points reachable from app code (verified via apktool smali on
// 2.2.1 and 2.4.0; obfuscated owner names differ per release, hence queries).
//
// Fingerprints deliberately avoid ad-unit IDs: those rotate between releases
// while the surrounding code shape does not. Anchors are SDK log wording,
// stable GMS framework types, and call-graph edges instead.
//
// Native ads: AdManager preload at startup and on-demand loads via AdLoader,
// rendered by a single native-ad composable (placements: top_ad, inline list
// card). The app's own kill-switch (adsDisabled flow) only flips once premium
// state resolves, and the startup preload races it.
// Rewarded/interstitial ads: RewardedAdManager preloads per-scenario units at
// app start with no premium check and loads via the interstitial SDK call.

val interstitialLoader = method("interstitial ad loader") {
    returns(Type.Void)
    paramCount(4)
    strings("Loading on UI thread")
}

val rewardedAdLoader = method("rewarded ad scenario loader") {
    returns(Type.Void)
    paramCount(1)
    calls(interstitialLoader)
}

val nativeAdLoader = method("native ad loader") {
    returns(Type.Void)
    paramCount(3)
    strings("Failed to build AdLoader.")
}

val nativeAdPreload = method("native ad preload") {
    returns(Type.Void)
    paramCount(1)
    calls(nativeAdLoader)
}

val nativeAdUi = method("native ad composable") {
    returns(Type.Void)
    paramCount(10)
    callsMethod { definingClass == "Lcom/google/android/gms/ads/nativead/NativeAd;" }
}

val disableAds = patch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1", "2.4.0"))
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
