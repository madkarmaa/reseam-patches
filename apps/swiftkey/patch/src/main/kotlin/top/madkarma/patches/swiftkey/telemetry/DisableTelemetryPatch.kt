// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.swiftkey.telemetry

import app.reseam.patch.*
import top.madkarma.patches.swiftkey.SwiftKeyPatchStrings

// Firebase reads these before the app configures it.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
        "google_analytics_adid_collection_enabled" to "false",
    )

val disableTelemetry =
    patch(SwiftKeyPatchStrings.disableTelemetryName) {
        description(SwiftKeyPatchStrings.disableTelemetryDescription)
        compatibleWith("com.touchtype.swiftkey")

        execute {
            queueTelemetryEvent.alwaysReturn()
            log.info("Telemetry: ${queueTelemetryEvent.descriptor} no longer queues events.")

            stageTelemetryUpload.alwaysReturn()
            log.info(
                "Telemetry: ${stageTelemetryUpload.descriptor} no longer stages queued events for upload.",
            )

            // Without an initialized SDK, Adjust drops every tracked event.
            initializeAdjust.alwaysReturn()
            log.info("Telemetry: ${initializeAdjust.descriptor} no longer starts the Adjust SDK.")

            manifest.edit {
                val application = findByTag("application").single()

                for ((name, value) in firebaseCollection) {
                    val existing = findByTag("meta-data").filter { it["android:name"] == name }
                    if (existing.size > 1) error("Telemetry: duplicate Firebase metadata $name")

                    val entry =
                        existing.singleOrNull()
                            ?: createElement("meta-data").also { application.appendChild(it) }

                    entry["android:name"] = name
                    entry["android:value"] = value
                }
            }
            log.info(
                "Telemetry: the manifest turns off Firebase Analytics, Crashlytics, and Sessions.",
            )
        }
    }
