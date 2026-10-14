// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.paypal.startup

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val disableStartupChecks = patch("Disable startup checks") {
    description("Prevents local integrity and emulator checks from crashing the app at startup.")
    compatibleWith("com.paypal.android.p2pmobile")

    execute {
        startupVerifier.alwaysReturn()
        log.info("Disabled startup verifier ${startupVerifier.descriptor}")
    }
}
