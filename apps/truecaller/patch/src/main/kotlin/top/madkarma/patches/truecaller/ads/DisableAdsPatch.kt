// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.ads

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val disableAds = patch("Disable ads") {
    description("Disables in-app ads.")
    compatibleWith("com.truecaller")

    execute {
        shouldShowAds.alwaysReturn(false)
        log.info("Ads: ${shouldShowAds.descriptor} disables ads.")
    }
}
