@file:Suppress("unused")

package top.madkarma.patches.niagara

import app.reseam.patch.after
import app.reseam.patch.appEntry
import top.madkarma.revisions.recordedPatch

val disableAnalytics = recordedPatch("Disable analytics") {
    description(
        "Refuses and blocks analytics collection."
    )
    compatibleWith("bitpit.launcher")

    execute {
        appEntry.after {
            call(NiagaraSetup.refuseAnalytics, thisObject)
            call(NiagaraSetup.disableAnalyticsFlags, thisObject)
        }

    }
}
