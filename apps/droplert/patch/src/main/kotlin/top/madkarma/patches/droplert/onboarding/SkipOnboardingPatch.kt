// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.droplert.onboarding

import app.reseam.patch.ExternalPatch
import app.reseam.patch.appEntry
import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.droplert.Prefs

val skipOnboarding = patch("Skip onboarding") {
    description("Skips the setup screens, landing directly on home.")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        appEntry {
            call(
                Prefs.putBoolean, application, string("has_completed_onboarding"), bool(true)
            )
        }
        log.info("Onboarding: setup screens skipped.")
    }
}
