// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.onboarding

import app.reseam.patch.*

private val launchOrderKey =
    klass("launchOrderKey") {
        enumConstants("ONBOARDING", "INTENT_BASED_ONBOARDING")
    }

private val onboardingKey = launchOrderKey.enumConstant("ONBOARDING")

// Adding launch keys replaces the "onboarding" key with the onboarding steps.
private val addLaunchKeys =
    method("addLaunchKeys") {
        hasParam(launchOrderKey.descriptor)
        accesses {
            owner(launchOrderKey.descriptor)
            name(onboardingKey.name)
        }
    }

// The onboarding launch order returns those steps.
internal val onboardingLaunchOrder =
    method("onboardingLaunchOrder") {
        calledBy(addLaunchKeys)
        returns("java.io.Serializable")
    }
