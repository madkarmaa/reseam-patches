// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.ilovepdf.premium

import app.reseam.patch.*
import top.madkarma.patches.ilovepdf.ILovePdfPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

private const val BOOLEAN = "Ljava/lang/Boolean;"

val unlockPremium =
    patch(ILovePdfPatchStrings.unlockPremiumName) {
        description(ILovePdfPatchStrings.unlockPremiumDescription)
        compatibleWith("com.ilovepdf.www")

        execute {
            userPremium.replace {
                returnValue(callStatic(BOOLEAN, "valueOf", "(Z)$BOOLEAN", bool(true)))
            }
            log.info("Premium: ${userPremium.descriptor} always reports a Premium user.")

            // Every guest now looks Premium, so the login link offer would claim a Premium plan the
            // guest never bought.
            premiumLinkSuggestion.alwaysReturn(false)
            log.info("Premium: ${premiumLinkSuggestion.descriptor} skips the Premium link offer.")

            // Only the Settings "Current membership" row shows this label.
            if (!resources.setStringEverywhere("premium_membership", PATCH_ATTRIBUTION))
                error("Premium: premium_membership string missing")
            log.info("Premium: premium_membership shows the shared patch attribution.")
        }
    }
