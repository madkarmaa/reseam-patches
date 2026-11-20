// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.timberradio.analytics

import app.reseam.patch.*
import top.madkarma.patches.timberradio.TimberRadioPatchStrings

// Firebase reads these before the app configures it.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
    )

val removeAnalytics =
    patch(TimberRadioPatchStrings.removeAnalyticsName) {
        description(TimberRadioPatchStrings.removeAnalyticsDescription)
        compatibleWith("com.msoft.timberradio")

        execute {
            // The player reports each played station and its stream to radar.php and
            // trending_report.php as STATISTICS uploads.
            uploadDataToServer.before {
                whenEqual(paramOfType(uploadType.descriptor), staticField(statisticsUpload)) {
                    returnVoid()
                }
            }
            log.info(
                "Analytics: ${uploadDataToServer.descriptor} drops listening statistics uploads.",
            )

            initializeAds.alwaysReturn()
            log.info("Analytics: ${initializeAds.descriptor} no longer starts the AdMob SDK.")

            manifest.edit {
                val application = findByTag("application").single()

                for ((name, value) in firebaseCollection) {
                    val existing = findByTag("meta-data").filter { it["android:name"] == name }
                    if (existing.size > 1) error("Analytics: duplicate Firebase metadata $name")

                    val entry =
                        existing.singleOrNull()
                            ?: createElement("meta-data").also { application.appendChild(it) }

                    entry["android:name"] = name
                    entry["android:value"] = value
                }
            }
            log.info(
                "Analytics: the manifest turns off Firebase Analytics, Crashlytics, and Sessions.",
            )
        }
    }
