// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.homeworkout.premium

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.homeworkout.HomeWorkoutPatchStrings
import top.madkarma.patches.universal.signature.signatureKiller

val unlockPremium =
    patch(HomeWorkoutPatchStrings.unlockPremiumName) {
        description(HomeWorkoutPatchStrings.unlockPremiumDescription)
        compatibleWith("homeworkout.homeworkouts.noequipment")
        // The native string decoder refuses to load unless the app has the developer's certificate.
        dependsOn(signatureKiller)

        execute {
            productNotPurchased.alwaysReturn(false)
            log.info(
                "Premium: ${productNotPurchased.descriptor} reports every product as purchased.",
            )
        }
    }
