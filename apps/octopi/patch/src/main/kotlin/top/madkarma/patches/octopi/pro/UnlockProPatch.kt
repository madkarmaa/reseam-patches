// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.octopi.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("com.otp.octopilauncher")

    execute {
        isPro.alwaysReturn(true)
        log.info("Pro: ${isPro.descriptor} returns an unlocked entitlement for all features.")
    }
}
