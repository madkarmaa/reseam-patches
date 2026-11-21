// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.strava.telemetry

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.before
import app.reseam.patch.patch
import app.reseam.patch.proto
import top.madkarma.patches.strava.StravaPatchStrings

private const val BRANCH_TRACKING_DISABLED = "bnc_tracking_state"

val disableTelemetry =
    patch(StravaPatchStrings.disableTelemetryName) {
        description(StravaPatchStrings.disableTelemetryDescription)
        compatibleWith("com.strava")

        execute {
            // Covers Strava's own events and Snowplow's automatic lifecycle and screen events.
            trackEvent.alwaysReturnNull()
            log.info("Telemetry: ${trackEvent.descriptor} drops every Snowplow event.")

            applyDataSharing.before { paramOfType(Type.Boolean).assign(bool(true)) }
            log.info(
                "Telemetry: ${applyDataSharing.descriptor} opts out of data sharing and Firebase Analytics.",
            )

            // Answers the registration consent prompt before it is shown.
            registrationFlowConstructor.after {
                thisObject.call(handleRegistrationEvent, staticField(rejectConsent))
            }
            log.info(
                "Telemetry: ${registrationFlowConstructor.descriptor} rejects data sharing without asking.",
            )

            branchPreferenceFlag.before {
                val trackingDisabled =
                    string(BRANCH_TRACKING_DISABLED)
                        .callVirtual(
                            Type.String,
                            "equals",
                            proto(Type.Boolean, Type.Object),
                            param(0),
                        )
                whenTrue(trackingDisabled) { returnTrue() }
            }
            log.info(
                "Telemetry: ${branchPreferenceFlag.descriptor} keeps Branch tracking disabled.",
            )
        }
    }
