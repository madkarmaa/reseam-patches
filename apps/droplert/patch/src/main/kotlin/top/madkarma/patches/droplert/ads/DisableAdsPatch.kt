// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.ads

import app.reseam.patch.*
import top.madkarma.patches.shared.isPresent

val disableAds = patch("Disable ads") {
    description("Disables in-app ads and their consent prompt.")
    compatibleWith(
        "com.shahzaman.pricetracker"(
            "2.2.1", "2.4.0", "2.4.1", "2.5.0", "2.5.1", "2.5.2", "2.5.3", "2.5.4"
        )
    )
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        if (isPresent(consentFormShow)) {
            consentFormShow.replace {
                paramOfType(consentFormDismissed.owner).call(consentFormDismissed, nullObject)
                returnVoid()
            }
            log.info("Ads: ${consentFormShow.descriptor} dismisses ad consent without showing a form.")
        } else if (isPresent(adStartupWithoutConsent)) {
            log.info("Ads: ${adStartupWithoutConsent.descriptor} uses the startup flow without ad consent.")
        } else error("Ads: unsupported consent flow")

        interstitialLoader.alwaysReturn()
        log.info("Ads: ${interstitialLoader.descriptor} skips interstitial loading.")

        rewardedAdLoader.alwaysReturn()
        log.info("Ads: ${rewardedAdLoader.descriptor} skips rewarded ad loading.")

        nativeAdUi.alwaysReturn()
        log.info("Ads: ${nativeAdUi.descriptor} skips native ad rendering.")

        if (isPresent(nativeAdLoader)) {
            nativeAdLoader.alwaysReturn()
            log.info("Ads: ${nativeAdLoader.descriptor} skips native ad loading.")

            nativeAdPreload.alwaysReturn()
            log.info("Ads: ${nativeAdPreload.descriptor} skips native ad preloading.")
        } else if (isPresent(nativeAdLoaderSuspend)) {
            nativeAdLoaderSuspend.alwaysReturnNull()
            log.info("Ads: ${nativeAdLoaderSuspend.descriptor} returns no native ad.")

            val preloads = nativeAdPreloadsSuspend.all
            if (preloads.isEmpty()) error("Ads: no native ad preloads found")

            preloads.forEach {
                it.alwaysReturn()
                log.info("Ads: ${it.descriptor} skips native ad preloading.")
            }
        } else {
            error("Ads: unsupported app version")
        }
    }
}
