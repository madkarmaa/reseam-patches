@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.invoke
import top.madkarma.patches.universal.removePairip
import top.madkarma.revisions.recordedPatch

val disableAds = recordedPatch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        val version = manifest.versionName

        runDisableAdsCommon()

        if (version in adsLegacyVersions) {
            nativeAdLoader.method.alwaysReturn()
            nativeAdPreload.method.alwaysReturn()
        }

        if (version in adsSuspendVersions) {
            nativeAdLoaderSuspend.method.alwaysReturnNull()
            nativeAdPreloadsSuspend.forEach { method.alwaysReturn() }
        }
    }
}
