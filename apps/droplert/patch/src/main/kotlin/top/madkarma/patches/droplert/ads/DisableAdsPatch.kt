// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.alwaysReturn
import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.shared.isPresent
import top.madkarma.patches.universal.pairip.removePairip

val disableAds = patch("Disable ads") {
    description("Disables in-app ads")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        interstitialLoader.alwaysReturn()
        rewardedAdLoader.alwaysReturn()
        nativeAdUi.alwaysReturn()

        if (isPresent(nativeAdLoader)) {
            nativeAdLoader.alwaysReturn()
            nativeAdPreload.alwaysReturn()
        } else if (isPresent(nativeAdLoaderSuspend)) {
            nativeAdLoaderSuspend.alwaysReturnNull()
            val preloads = nativeAdPreloadsSuspend.all
            if (preloads.isEmpty()) error("Ads: no native ad preloads found")
            preloads.forEach { it.alwaysReturn() }
        } else {
            error("Ads: unsupported app version")
        }
    }
}
