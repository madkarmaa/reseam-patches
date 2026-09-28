@file:Suppress("unused")

package top.madkarma.patches.niagara

import app.reseam.patch.appEntry
import app.reseam.patch.patch

val disableAnalytics = patch("Disable analytics") {
    description(
        "Refuses and blocks analytics collection."
    )
    compatibleWith("bitpit.launcher")

    execute {
        appEntry {
            call(NiagaraSetup.refuseAnalytics, application)
            call(NiagaraSetup.disableAnalyticsFlags, application)
        }
        log.info("Analytics: collection refused and blocked at startup.")
    }
}
