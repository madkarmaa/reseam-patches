// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.textra.telemetry

import app.reseam.patch.alwaysReturn
import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.patch
import top.madkarma.patches.textra.TextraPatchStrings
import top.madkarma.patches.textra.shared.textraIntegrity

val disableTelemetry =
    patch(TextraPatchStrings.disableTelemetryName) {
        description(TextraPatchStrings.disableTelemetryDescription)
        compatibleWith("com.textra")
        dependsOn(textraIntegrity)

        execute {
            val clientReports = reportClientTelemetry.all
            if (clientReports.isEmpty()) error("Telemetry: no Google client reporters found")

            for (target in listOf(fetchAdConfiguration, fetchTrialLicense, createLoggingBackend)) {
                target.alwaysReturnNull()
                log.info(
                    "Telemetry: ${target.descriptor} returns null to prevent reporting requests.",
                )
            }

            for (target in
                clientReports +
                    listOf(
                        initializeInMobi,
                        initializeAmazon,
                        initializeSmaato,
                        initializeTappx,
                        initializeConsent,
                        recordAdDebug,
                    )) {
                target.alwaysReturn()
                log.info(
                    "Telemetry: ${target.descriptor} skips SDK collection and event recording.",
                )
            }
        }
    }
