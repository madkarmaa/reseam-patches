// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kleinanzeigen.share

import app.reseam.patch.patch
import app.reseam.patch.replace

val removeShareTracking = patch("Remove share tracking") {
    description("Removes tracking parameters from shared ad, seller, and profile links.")
    compatibleWith("com.ebay.kleinanzeigen")

    execute {
        appendShareTracking.replace { returnValue(param(0)) }
        log.info("Share: ${appendShareTracking.descriptor} returns the link unchanged.")

        if (bytecode.replaceAllStrings(
                PROFILE_SHARE_TRACKING, ""
            ) == 0
        ) error("Share: profile share tracking not found")
        log.info("Share: profile share links no longer carry tracking parameters.")
    }
}
