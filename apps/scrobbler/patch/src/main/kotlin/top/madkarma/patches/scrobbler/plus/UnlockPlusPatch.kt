// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.after
import app.reseam.patch.patch

val unlockPlus = patch("Unlock Plus") {
    description("Unlocks Plus-only features.")
    compatibleWith("com.arn.scrobble")

    execute {
        checkLicense.after {
            whenInstanceOf(result, licenseState.descriptor) {
                returnValue(staticField(validLicense))
            }
        }
        log.info("Plus: unlocked via ${checkLicense.descriptor}.")
    }
}
