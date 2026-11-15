// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.intro

import app.reseam.patch.*
import top.madkarma.extensions.bindings.NiagaraSetup
import top.madkarma.patches.niagara.NiagaraPatchStrings
import top.madkarma.patches.shared.isPresent

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

        val skipTutorialsOption =
            boolOption(
                "skipTutorials",
                title = NiagaraPatchStrings.skipIntroSkipTutorialsTitle,
                description = NiagaraPatchStrings.skipIntroSkipTutorialsDescription,
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

            if (options[skipTutorialsOption]) skipTutorials()
        }
    }

// Only some versions show these tutorials, so each is skipped where its variant reader exists.
private fun PatchRuntime.skipTutorials() {
    onboardingTutorials.forEach { tutorial ->
        if (!isPresent(tutorial.readVariant)) return@forEach

        tutorial.readVariant.before { returnValue(staticField(tutorial.skip)) }
        log.info("Intro: ${tutorial.readVariant.descriptor} skips the ${tutorial.description}.")
    }
}
