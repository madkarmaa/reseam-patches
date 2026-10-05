// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.alltrails.peak

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPeak = patch("Unlock Peak") {
    description("Unlocks Peak-only features.")
    compatibleWith("com.alltrails.alltrails")

    execute {
        userIsPro.alwaysReturn(true)
        log.info("Peak: ${userIsPro.descriptor} forced true.")

        userSubscriptionTier.alwaysReturn("peak")
        log.info("Peak: ${userSubscriptionTier.descriptor} forced to peak.")

        val peakName = resources.getString("peak_display_name")
            ?: error("Unlock Peak: peak_display_name string missing")

        val signedPeakName = "$peakName | $PATCH_ATTRIBUTION"
        if (!resources.setString("peak_display_name", signedPeakName))
            error("Unlock Peak: peak_display_name string not writable")

        log.info("Peak: plan label tagged.")
    }
}
