// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.telemetry

import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.extensions.bindings.NiagaraSetup
import top.madkarma.patches.niagara.NiagaraPatchStrings

val disableTelemetry =
    patch(NiagaraPatchStrings.disableTelemetryName) {
        description(NiagaraPatchStrings.disableTelemetryDescription)
        compatibleWith("bitpit.launcher")

        execute {
            appEntry {
                call(NiagaraSetup.refuseAnalytics, application)
                call(NiagaraSetup.disableTelemetryFlags, application)
            }
            log.info("Telemetry: collection refused and blocked at startup.")
        }
    }
