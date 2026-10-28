// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.onboarding

import app.reseam.patch.method

// The launch order's "onboarding" entry expands to the onboarding steps this returns.
internal val onboardingLaunchOrder = method("onboardingLaunchOrder") {
    calls {
        owner($$"com.picsart.growth.launchorder.impl.data.RealLaunchOrderProvider$getOnboardingLaunchOrder$1")
        name("<init>")
    }
}
