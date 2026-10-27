// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.annoyances

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.capcut.integrity.bypassIntegrityChecks

val removeAnnoyances = patch("Remove annoyances") {
    description("Removes app annoyances, upsells and popups.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(bypassIntegrityChecks)

    execute {
        startupQuestionnaireEnabled.alwaysReturn(false)
        log.info("Annoyances: ${startupQuestionnaireEnabled.descriptor} skips the startup questionnaire.")

        coldStartPromotionEligible.alwaysReturn(false)
        log.info("Annoyances: ${coldStartPromotionEligible.descriptor} declines cold-start tool promotions.")

        templateLandingGuideRegistration.alwaysReturn(false)
        log.info("Annoyances: ${templateLandingGuideRegistration.descriptor} skips the template interests and view-mode survey.")

        // A suspend function completes immediately by returning Unit, without resuming its continuation.
        desktopPromotion.replace { returnValue(staticField(kotlinUnit)) }
        log.info("Annoyances: ${desktopPromotion.descriptor} skips the desktop Pro promotion.")

        desktopLoginPromotionRegistration.replace { returnValue(staticField(kotlinUnit)) }
        log.info("Annoyances: ${desktopLoginPromotionRegistration.descriptor} skips the desktop sign-in promotion.")

        // The caller continues its normal completion path when no promotion is eligible.
        exportPromotionEligible.alwaysReturn(false)
        log.info("Annoyances: ${exportPromotionEligible.descriptor} declines post-export campaign and subscription promotions.")
    }
}
