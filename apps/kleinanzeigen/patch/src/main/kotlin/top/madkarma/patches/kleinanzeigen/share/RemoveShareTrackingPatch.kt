// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kleinanzeigen.share

import app.reseam.patch.patch
import top.madkarma.patches.kleinanzeigen.KleinanzeigenPatchStrings
import top.madkarma.patches.shared.clearurls.cleanSharedLinks
import top.madkarma.patches.shared.clearurls.clearUrls

val removeShareTracking =
    patch(KleinanzeigenPatchStrings.removeShareTrackingName) {
        description(
            KleinanzeigenPatchStrings.removeShareTrackingDescription,
        )
        compatibleWith("com.ebay.kleinanzeigen")
        dependsOn(clearUrls)

        execute {
            val redirectedCalls = cleanSharedLinks()
            log.info(
                "Share: $redirectedCalls Intent.createChooser calls remove tracking with the ClearURLs rules.",
            )
        }
    }
