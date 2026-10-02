// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.droplert.ads

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val supportedVersions = setOf("2.2.1", "2.4.0", "2.4.1", "2.5.0", "2.5.1", "2.5.2")

internal val interstitialLoader = method("interstitialLoader") {
    strings("Loading on UI thread")
}

internal val rewardedAdLoader = method("rewardedAdLoader") {
    calls(interstitialLoader)
    stringsStartingWith("ca-app-pub-")
}

private val productDetails = method("productDetails") {
    strings("PRICE HISTORY")
}

// The product-details renderer delegates its native ad to this composable.
internal val nativeAdUi = method("nativeAdUi") {
    calledBy(productDetails)
    calls { owner("com.google.android.gms.ads.nativead.NativeAd") }
}

// The direct loader accepts an ad-unit ID; the coroutine body and SDK paths do not.
internal val nativeAdLoader = method("nativeAdLoader") {
    strings("Failed to build AdLoader.")
    hasParam(Type.String)
}

internal val nativeAdPreload = method("nativeAdPreload") {
    calls(nativeAdLoader)
    returns(Type.Void)
}

internal val nativeAdLoaderSuspend = method("nativeAdLoaderSuspend") {
    strings("Failed to build AdLoader.")
    returns(Type.Object)
}

private val adManager = klass("adManager") {
    strings("Failed to check cached premium status")
}

internal val nativeAdPreloadsSuspend = adManager.methods("nativeAdPreloadsSuspend") {
    params(Type.String)
    returns(Type.Void)
}
