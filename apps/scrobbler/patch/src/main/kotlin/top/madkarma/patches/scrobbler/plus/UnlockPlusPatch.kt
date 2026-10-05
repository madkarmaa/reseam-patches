// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.after
import app.reseam.patch.patch

val unlockPlus = patch("Unlock Plus") {
    description("Unlocks Plus-only features.")
    compatibleWith("com.arn.scrobble")

    execute {
        checkLicense.after {
            whenInstanceOf(capture("result"), licenseState.descriptor) {
                returnValue(call(licenseStateValueOf, string("VALID")))
            }
        }
        log.info("Plus: unlocked via ${checkLicense.descriptor}.")
    }
}
