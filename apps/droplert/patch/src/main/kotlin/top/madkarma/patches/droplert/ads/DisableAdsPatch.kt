@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

val nativeAdLoader = method("native ad loader") {
    paramCount(3)
    strings("Failed to build AdLoader.")
}

val nativeAdPreload = method("native ad preload") {
    returns(Type.Void)
    calls(nativeAdLoader)
}

val disableAds = patch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1", "2.4.0", "2.4.1"))
    dependsOn(removePairip)

    execute {
        runDisableAdsCommon()
        nativeAdLoader.method.alwaysReturn()
        nativeAdPreload.method.alwaysReturn()
        log.info("Ads: neutralized native, rewarded, and interstitial entry points.")
    }
}
