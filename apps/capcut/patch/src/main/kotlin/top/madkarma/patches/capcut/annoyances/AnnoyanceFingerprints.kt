// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.annoyances

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal val kotlinUnit = klass("kotlin.Unit").field("INSTANCE")

internal val templateLandingGuideRegistration = method("templateLandingGuideRegistration") {
    strings("register landing guide: ")
}

internal val desktopPromotion = method("desktopPromotion") {
    strings("is_claimed", "frequency_check", "capcutpc_ad_placement")
}

internal val desktopLoginPromotionRegistration = method("desktopLoginPromotionRegistration") {
    inClass(klass(desktopPromotion.owner))
    strings("pc_guide_login")
}

internal val startupQuestionnaireEnabled = method("startupQuestionnaireEnabled") {
    strings("auto test hide questionnaire")
}

internal val coldStartPromotionEligible = method("coldStartPromotionEligible") {
    strings("canShowColdBootAlert popCenter disable")
}

internal val exportPromotionEligible = method("exportPromotionEligible") {
    strings("export success but vimo popup config not ready, hasRes = ")
}
