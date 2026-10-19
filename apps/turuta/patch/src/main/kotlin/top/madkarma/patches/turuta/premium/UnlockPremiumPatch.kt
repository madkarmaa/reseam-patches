// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.turuta.premium

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.turuta.extensions.bindings.PremiumPreferences

val unlockPremium = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("pe.tumicro.android")

    execute {
        requirePremiumPreferences(files)

        attachPreferences.after {
            call(
                PremiumPreferences.enable,
                thisObject.fieldOfType("android.content.SharedPreferences")
            )
        }
        log.info("Premium: ${attachPreferences.descriptor} initializes the premium preferences before Flutter startup.")

        // Both purchase queries and purchase updates pass through this Flutter converter.
        convertPurchases.before {
            val purchases = paramOfType("java.util.List")
            purchases.assign(call(PremiumPreferences.withPremium, purchases))
        }
        log.info("Premium: ${convertPurchases.descriptor} includes a lifetime purchase in Flutter purchase results.")
    }
}
