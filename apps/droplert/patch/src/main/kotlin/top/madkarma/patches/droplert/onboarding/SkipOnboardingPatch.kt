@file:Suppress("unused")

package top.madkarma.patches.droplert.onboarding

import app.reseam.patch.appEntry
import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.droplert.premium.Prefs
import top.madkarma.patches.universal.pairip.removePairip

val skipOnboarding = patch("Skip onboarding") {
    description("Skips the setup screens, landing directly on home.")
    compatibleWith("com.shahzaman.pricetracker"(*supportedVersions.toTypedArray()))
    dependsOn(removePairip)

    execute {
        appEntry {
            call(
                Prefs.putBoolean, application, string("has_completed_onboarding"), bool(true)
            )
        }
        log.info("Onboarding: setup screens skipped.")
    }
}
