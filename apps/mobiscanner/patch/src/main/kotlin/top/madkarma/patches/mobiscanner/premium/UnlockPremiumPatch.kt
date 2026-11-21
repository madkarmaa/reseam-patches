// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mobiscanner.premium

import app.reseam.patch.*
import top.madkarma.patches.mobiscanner.MobiScannerPatchStrings
import top.madkarma.patches.mobiscanner.shared.mobiScannerIntegrity
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPremium =
    patch(MobiScannerPatchStrings.unlockPremiumName) {
        description(MobiScannerPatchStrings.unlockPremiumDescription)
        compatibleWith("com.mobisystems.mobiscanner")
        dependsOn(mobiScannerIntegrity)

        execute {
            paidStatus.alwaysReturn(true)
            log.info("Premium: ${paidStatus.descriptor} always reports a paid user.")

            featureAccess.alwaysReturn(true)
            log.info("Premium: ${featureAccess.descriptor} allows every Premium and OCR feature.")

            // Only the Settings "Manage Subscription" row shows this label.
            if (!resources.setStringEverywhere("manage_subscription", PATCH_ATTRIBUTION))
                error("Premium: manage_subscription string missing")
            log.info("Premium: manage_subscription shows the shared patch attribution.")
        }
    }
