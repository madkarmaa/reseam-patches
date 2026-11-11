// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.annoyances

import app.reseam.patch.*
import top.madkarma.patches.droplert.DroplertPatchStrings

val removeAnnoyances =
    patch(DroplertPatchStrings.removeAnnoyancesName) {
        description(DroplertPatchStrings.removeAnnoyancesDescription)
        compatibleWith("com.shahzaman.pricetracker"("2.6.0"))
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            requestReview.alwaysReturn()
            log.info("Annoyances: ${requestReview.descriptor} skips rating requests.")
        }
    }
