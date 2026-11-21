// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.zoiper.combo

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.zoiper.ZoiperPatchStrings

val unlockCombo =
    patch(ZoiperPatchStrings.unlockComboName) {
        description(ZoiperPatchStrings.unlockComboDescription)
        compatibleWith("com.zoiper.android.app")

        execute {
            comboEnabled.alwaysReturn(true)
            log.info("Combo: ${comboEnabled.descriptor} reports an active Combo subscription.")

            // The Premium features screen describes managing the active subscription here.
            val manageSubscription = "premium_features_activity_manage_purchases_description"
            if (!resources.setStringEverywhere(manageSubscription, PATCH_ATTRIBUTION))
                error("Combo: $manageSubscription string missing")
            log.info("Combo: $manageSubscription shows the shared patch attribution.")
        }
    }
