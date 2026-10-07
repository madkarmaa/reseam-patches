// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.analytics

import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.patches.niagara.NiagaraSetup

val disableAnalytics = patch("Disable analytics") {
    description("Refuses and blocks analytics collection.")
    compatibleWith("bitpit.launcher")

    execute {
        appEntry {
            call(NiagaraSetup.refuseAnalytics, application)
            call(NiagaraSetup.disableAnalyticsFlags, application)
        }
        log.info("Analytics: collection refused and blocked at startup.")
    }
}
