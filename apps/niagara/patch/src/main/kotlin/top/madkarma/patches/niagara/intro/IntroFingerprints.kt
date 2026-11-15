// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.intro

import app.reseam.patch.*

/**
 * An onboarding step whose variant Niagara reads from remote config. Its SKIP variant passes the
 * step without showing it.
 */
internal class OnboardingTutorial(
    val description: String,
    remoteKey: String,
    variantClass: String,
) {
    private val variants = klass(variantClass)

    val skip = variants.enumConstant("SKIP")

    val readVariant =
        method("${remoteKey}Reader") {
            strings(remoteKey)
            returns(variants.descriptor)
        }
}

internal val onboardingTutorials =
    listOf(
        OnboardingTutorial(
            "favorites highlight",
            "onboarding_highlight_favorites_variant",
            "bitpit.launcher.savesystem.OnboardingHighlightFavoritesVariant",
        ),
        OnboardingTutorial(
            "alphabet swipe tutorial",
            "onboarding_alphabet_tutorial_variant",
            "bitpit.launcher.savesystem.OnboardingAlphabetTutorialVariant",
        ),
    )
