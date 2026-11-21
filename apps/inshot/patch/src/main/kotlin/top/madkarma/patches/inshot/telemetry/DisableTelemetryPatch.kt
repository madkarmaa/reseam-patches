// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.inshot.telemetry

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.inshot.InShotPatchStrings
import top.madkarma.patches.universal.signature.signatureKiller

// Firebase and the Facebook SDK read these before the app configures them.
private val collectionSwitches =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "google_analytics_adid_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
        "com.facebook.sdk.AutoInitEnabled" to "false",
        "com.facebook.sdk.AutoLogAppEventsEnabled" to "false",
        "com.facebook.sdk.AdvertiserIDCollectionEnabled" to "false",
    )

val disableTelemetry =
    patch(InShotPatchStrings.disableTelemetryName) {
        description(InShotPatchStrings.disableTelemetryDescription)
        compatibleWith("com.camerasideas.instashot")
        // The native video engine kills the app when its signing certificate changes.
        dependsOn(signatureKiller)

        execute {
            logEvent.alwaysReturn()
            log.info("Telemetry: ${logEvent.descriptor} drops every app event.")

            manifest.edit {
                val application = findByTag("application").single()

                for ((name, value) in collectionSwitches) {
                    val existing = findByTag("meta-data").filter { it["android:name"] == name }
                    if (existing.size > 1) error("Telemetry: duplicate manifest metadata $name")

                    val entry =
                        existing.singleOrNull()
                            ?: createElement("meta-data").also { application.appendChild(it) }

                    entry["android:name"] = name
                    entry["android:value"] = value
                }
            }
            log.info(
                "Telemetry: the manifest turns off Firebase Analytics, Crashlytics, Sessions, " +
                    "and Facebook app events.",
            )
        }
    }
