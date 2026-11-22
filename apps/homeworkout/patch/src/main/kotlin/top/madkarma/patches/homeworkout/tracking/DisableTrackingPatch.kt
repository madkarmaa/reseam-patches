// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.homeworkout.tracking

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.homeworkout.HomeWorkoutPatchStrings
import top.madkarma.patches.universal.signature.signatureKiller

// Firebase and the Facebook SDK read these before the app configures them. The Facebook SDK still
// initializes, so Facebook sign-in keeps working.
private val collectionSwitches =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "google_analytics_adid_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
        "com.facebook.sdk.AutoLogAppEventsEnabled" to "false",
        "com.facebook.sdk.AdvertiserIDCollectionEnabled" to "false",
    )

val disableTracking =
    patch(HomeWorkoutPatchStrings.disableTrackingName) {
        description(HomeWorkoutPatchStrings.disableTrackingDescription)
        compatibleWith("homeworkout.homeworkouts.noequipment")
        // The native string decoder refuses to load unless the app has the developer's certificate.
        dependsOn(signatureKiller)

        execute {
            logEvent.alwaysReturn()
            log.info("Tracking: ${logEvent.descriptor} drops every app event.")

            manifest.edit {
                val application = findByTag("application").single()

                for ((name, value) in collectionSwitches) {
                    val existing = findByTag("meta-data").filter { it["android:name"] == name }
                    if (existing.size > 1) error("Tracking: duplicate manifest metadata $name")

                    val entry =
                        existing.singleOrNull()
                            ?: createElement("meta-data").also { application.appendChild(it) }

                    entry["android:name"] = name
                    entry["android:value"] = value
                }
            }
            log.info(
                "Tracking: the manifest turns off Firebase Analytics, Crashlytics, Sessions, " +
                    "and Facebook app events.",
            )
        }
    }
