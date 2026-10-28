// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.onboarding

import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.patch

val skipOnboarding = patch("Skip onboarding") {
    description("Skips the onboarding questionnaires shown at startup.")
    compatibleWith("com.picsart.studio")

    execute {
        onboardingLaunchOrder.alwaysReturnNull()
        log.info("Onboarding: ${onboardingLaunchOrder.descriptor} adds no onboarding steps to the launch order.")
    }
}
