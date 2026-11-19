// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.alwaysonwidgets.analytics

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.alwaysonwidgets.AlwaysOnWidgetsPatchStrings

// Firebase reads these before the app configures it; the app's consent handler can re-enable
// collection, but not after deactivation.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
    )

// Initializes Meta Audience Network at process start, which samples the motion sensors.
private const val AUDIENCE_NETWORK_PROVIDER = "com.facebook.ads.AudienceNetworkContentProvider"

val removeAnalytics =
    patch(AlwaysOnWidgetsPatchStrings.removeAnalyticsName) {
        description(AlwaysOnWidgetsPatchStrings.removeAnalyticsDescription)
        compatibleWith("xyz.inspoapps.alwaysonwidgets")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Take the app's own path for a build without an AdMob app ID: no consent form, ads
            // blocked.
            consentManagerConstructor.after { thisObject.set(adsConfigured, bool(false)) }
            log.info(
                "Analytics: ${consentManagerConstructor.descriptor} marks ads as unconfigured, skipping the consent form.",
            )

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

                findByTag("provider")
                    .singleOrNull { it["android:name"] == AUDIENCE_NETWORK_PROVIDER }
                    ?.remove() ?: error("Analytics: $AUDIENCE_NETWORK_PROVIDER provider missing")
            }
            log.info(
                "Analytics: the manifest turns off Firebase Analytics, Crashlytics, and Sessions.",
            )
            log.info("Analytics: the manifest no longer starts Meta Audience Network with the app.")
        }
    }
