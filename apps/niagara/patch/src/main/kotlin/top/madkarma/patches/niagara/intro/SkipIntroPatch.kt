// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.intro

import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.patches.niagara.NiagaraSetup

val skipIntro = patch("Skip intro") {
    description("Skips the promo and terms screens, landing directly on setup.")
    compatibleWith("bitpit.launcher")

    val acceptTermsOption = boolOption(
        "acceptTerms",
        title = "Accept terms",
        description = "Marks the terms as accepted, skipping the terms screen.",
        default = true,
    )

    val skipToFavoritesOption = boolOption(
        "skipToFavorites",
        title = "Skip to favorites",
        description = "Jumps the onboarding flow straight to the favorites setup step.",
        default = true,
    )

    execute {
        if (options[acceptTermsOption]) {
            appEntry {
                call(NiagaraSetup.acceptTerms, application)
            }
            log.info("Intro: terms accepted at startup.")
        }

        if (options[skipToFavoritesOption]) {
            appEntry {
                call(NiagaraSetup.skipToFavoritesSetup, application)
            }
            log.info("Intro: skipping straight to favorites setup.")
        }
    }
}
