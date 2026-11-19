// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.duomusic.analytics

import app.reseam.patch.patch
import top.madkarma.patches.duomusic.DuoMusicPatchStrings

// Firebase reads these before the app configures it; the app's own Crashlytics reports are dropped
// while collection is off.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
    )

val removeAnalytics =
    patch(DuoMusicPatchStrings.removeAnalyticsName) {
        description(DuoMusicPatchStrings.removeAnalyticsDescription)
        compatibleWith("com.orhotechnologies.orhoduomusic")

        execute {
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
