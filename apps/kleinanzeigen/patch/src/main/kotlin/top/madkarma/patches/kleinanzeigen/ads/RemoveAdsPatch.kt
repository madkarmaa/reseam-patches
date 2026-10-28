// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kleinanzeigen.ads

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.kleinanzeigen.ads.bindings.Ads

val removeAds = patch("Remove ads") {
    description("Removes banner, native, and interstitial ads.")
    compatibleWith("com.ebay.kleinanzeigen")

    execute {
        parseAdsConfiguration.after { Ads.clearPlacements(thisObject.field(adPlacements)) }
        log.info("Ads: ${parseAdsConfiguration.descriptor} leaves ${adPlacements.name} without ad placements.")
    }
}
