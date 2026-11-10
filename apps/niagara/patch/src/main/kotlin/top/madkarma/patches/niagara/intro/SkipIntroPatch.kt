// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.intro

import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.extensions.bindings.NiagaraSetup
import top.madkarma.patches.niagara.NiagaraPatchStrings

val skipIntro =
    patch(NiagaraPatchStrings.skipIntroName) {
        description(NiagaraPatchStrings.skipIntroDescription)
        compatibleWith("bitpit.launcher")

        val acceptTermsOption =
            boolOption(
                "acceptTerms",
                title = NiagaraPatchStrings.skipIntroAcceptTermsTitle,
                description = NiagaraPatchStrings.skipIntroAcceptTermsDescription,
                default = true,
            )

        val skipToFavoritesOption =
            boolOption(
                "skipToFavorites",
                title = NiagaraPatchStrings.skipIntroSkipToFavoritesTitle,
                description = NiagaraPatchStrings.skipIntroSkipToFavoritesDescription,
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
