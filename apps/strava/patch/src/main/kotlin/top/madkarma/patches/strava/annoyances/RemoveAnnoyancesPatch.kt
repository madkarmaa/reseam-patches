// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.strava.annoyances

import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.extensions.strava.bindings.RegistrationSteps
import top.madkarma.patches.strava.StravaPatchStrings

val removeAnnoyances =
    patch(StravaPatchStrings.removeAnnoyancesName) {
        description(StravaPatchStrings.removeAnnoyancesDescription)
        compatibleWith("com.strava")

        execute {
            // Covers the focus, loss-aversion, upsell and device-connect sheets.
            youTabOverlay.replace { returnValue(staticField(noOverlayInstance)) }
            log.info("Annoyances: ${youTabOverlay.descriptor} opens no sheet over the You tab.")

            buildRegistrationSteps.after { returnValue(RegistrationSteps.keepRequired(result)) }
            log.info(
                "Annoyances: ${buildRegistrationSteps.descriptor} keeps only the name and birthday registration steps.",
            )

            showRecordPrompt.alwaysReturn()
            log.info(
                "Annoyances: ${showRecordPrompt.descriptor} skips the post-sign-up record prompt.",
            )

            contactSharingEligible.alwaysReturn(false)
            log.info(
                "Annoyances: ${contactSharingEligible.descriptor} no longer allows the contact sharing sheet.",
            )
        }
    }
