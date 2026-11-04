// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.respiro.premium

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.respiro.preferences.readPreference
import top.madkarma.respiro.preferences.bindings.Preferences

val unlockPremium = patch("Unlock premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.sgnoogle.respiro")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        readPreference.after {
            result.assign(call(Preferences.premiumValue, param(0), result))
        }
        log.info("Premium: ${readPreference.descriptor} overrides premium and widget preferences by key.")

        validSigningCertificate.alwaysReturn(true)
        log.info("Premium: ${validSigningCertificate.descriptor} accepts the patched app's signature.")
    }
}
