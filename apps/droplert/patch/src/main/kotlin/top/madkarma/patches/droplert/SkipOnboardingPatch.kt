@file:Suppress("unused")

package top.madkarma.patches.droplert

import app.reseam.patch.appEntry
import app.reseam.patch.before
import app.reseam.patch.invoke
import app.reseam.patch.patch
import top.madkarma.patches.droplert.premium.Prefs
import top.madkarma.patches.universal.removePairip

val skipOnboarding = patch("Skip onboarding") {
    description("Skips the setup screens, landing directly on home.")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1", "2.4.0", "2.4.1", "2.5.0"))
    dependsOn(removePairip)

    execute {
        appEntry.before {
            call(
                Prefs.putBoolean, thisObject, string("has_completed_onboarding"), bool(true)
            )
        }
        log.info("Onboarding: setup screens skipped.")
    }
}
