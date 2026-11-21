// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kyulauncher.premium

import app.reseam.patch.*
import top.madkarma.patches.kyulauncher.KyuLauncherPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

private const val PREMIUM_DESCRIPTION_KEY = "premium_desc"

val unlockPremium =
    patch(KyuLauncherPatchStrings.unlockPremiumName) {
        description(KyuLauncherPatchStrings.unlockPremiumDescription)
        compatibleWith("com.overdevs.kyulauncher")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Purchase queries report no Premium purchase, so the setter ignores what it's given.
            setPremium.before { paramOfType(Type.Boolean).assign(bool(true)) }
            log.info("Premium: ${setPremium.descriptor} always stores Premium as purchased.")

            // Without a Play Store connection, no purchase query runs to set the state.
            connectBilling.before { call(setPremium, bool(true)) }
            log.info("Premium: ${connectBilling.descriptor} unlocks Premium at startup.")

            check(resources.setStringEverywhere(PREMIUM_DESCRIPTION_KEY, PATCH_ATTRIBUTION)) {
                "Premium: string $PREMIUM_DESCRIPTION_KEY is missing"
            }
            log.info("Premium: $PREMIUM_DESCRIPTION_KEY shows the shared patch attribution.")
        }
    }
