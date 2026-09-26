@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.invoke
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.universal.removePairip
import top.madkarma.revisions.recordedPatch

val disableAds = recordedPatch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        runDisableAdsCommon()

        if (isPresent(nativeAdLoader)) {
            nativeAdLoader.method.alwaysReturn()
            nativeAdPreload.method.alwaysReturn()
        } else if (isPresent(nativeAdLoaderSuspend)) {
            nativeAdLoaderSuspend.method.alwaysReturnNull()
            nativeAdPreloadsSuspend.forEach { method.alwaysReturn() }
        } else {
            error("Ads: unsupported app version")
        }
    }
}
