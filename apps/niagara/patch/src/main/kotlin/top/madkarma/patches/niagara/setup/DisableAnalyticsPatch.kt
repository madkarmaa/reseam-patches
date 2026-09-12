package top.madkarma.patches.niagara.setup

import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.patch

val disableAnalytics =
    patch("Disable analytics") {
        description(
            "Refuses analytics collection (marketing and functional) and forces the " +
                "analytics feature flags off. Firebase collection stays off as shipped."
        )
        compatibleWith("bitpit.launcher")

        execute {
            appEntry.after {
                call(NiagaraSetup.refuseAnalytics, thisObject)
                call(NiagaraSetup.disableAnalyticsFlags, thisObject)
            }
        }
    }
