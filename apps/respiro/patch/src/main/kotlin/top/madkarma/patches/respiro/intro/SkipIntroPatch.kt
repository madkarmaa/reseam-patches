// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.respiro.intro

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.patches.respiro.preferences.readPreference
import top.madkarma.respiro.preferences.bindings.Preferences

val skipIntro = patch("Skip intro") {
    description("Skips the first-launch tutorial, release tour, and long welcome animation.")
    compatibleWith("com.sgnoogle.respiro")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        readPreference.after {
            result.assign(call(Preferences.introValue, param(0), result))
        }
        log.info("Intro: ${readPreference.descriptor} reports the tutorial and release tour as completed.")

        appEntry {
            call(Preferences.skipSplash, application)
        }
        log.info("Intro: app startup initializes the short splash transition.")
    }
}
