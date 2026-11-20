// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.at4k.premium

import app.reseam.patch.*
import top.madkarma.patches.at4k.At4kPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

private const val PREMIUM_PITCH = "Support an independent launcher built by a single developer."

val unlockPremium =
    patch(At4kPatchStrings.unlockPremiumName) {
        description(At4kPatchStrings.unlockPremiumDescription)
        compatibleWith("com.overdevs.at4k")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Purchase queries report no Premium purchase, so the setter ignores what it's given.
            setPremium.before { paramOfType(Type.Boolean).assign(bool(true)) }
            log.info("Premium: ${setPremium.descriptor} always stores Premium as purchased.")

            // Without a Play Store connection, no purchase query runs to set the state.
            connectBilling.before { call(setPremium, bool(true)) }
            log.info("Premium: ${connectBilling.descriptor} unlocks Premium at startup.")

            // The Premium dialog's body asks for a purchase above its "Premium Unlocked" button.
            if (bytecode.replaceStringsStartingWith(PREMIUM_PITCH) { PATCH_ATTRIBUTION } != 1)
                error("Premium: expected one Premium dialog description")
            log.info("Premium: the Premium dialog shows the shared patch attribution.")
        }
    }
