// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.universal.pairip.removePairip

val disableAds = patch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        runDisableAdsCommon()

        if (isPresent(nativeAdLoader)) {
            nativeAdLoader.method.alwaysReturn()
            nativeAdPreload.method.alwaysReturn()
            log.info("Ads: legacy loader pair patched.")
        } else if (isPresent(nativeAdLoaderSuspend)) {
            nativeAdLoaderSuspend.method.alwaysReturnNull()
            nativeAdPreloadsSuspend.forEach { method.alwaysReturn() }
            log.info("Ads: suspend loader patched.")
        } else {
            error("Ads: unsupported app version")
        }
    }
}
