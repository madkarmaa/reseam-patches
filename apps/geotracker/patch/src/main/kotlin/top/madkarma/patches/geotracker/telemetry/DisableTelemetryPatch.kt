// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.geotracker.telemetry

import app.reseam.patch.*
import top.madkarma.geotracker.settings.bindings.SettingsSections
import top.madkarma.patches.geotracker.GeoTrackerPatchStrings

// Firebase reads these before the app applies its privacy switches. Deactivation is permanent, so
// a later setAnalyticsCollectionEnabled(true) or setPerformanceCollectionEnabled(true) is ignored.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_performance_collection_deactivated" to "true",
        "firebase_sessions_enabled" to "false",
        "google_analytics_adid_collection_enabled" to "false",
    )

val disableTelemetry =
    patch(GeoTrackerPatchStrings.disableTelemetryName) {
        description(GeoTrackerPatchStrings.disableTelemetryDescription)
        compatibleWith("com.ilyabogdanovich.geotracker")

        execute {
            // Usage statistics, crash reports and diagnostics, in the order the app declares them.
            dataCollectionConstructor.before {
                param(0).assign(bool(false))
                param(1).assign(bool(false))
                param(2).assign(bool(false))
            }
            log.info(
                "Telemetry: ${dataCollectionConstructor.descriptor} keeps Firebase Analytics, Crashlytics and Performance off.",
            )

            // Settings' only Boolean values are the usage statistics, crash report and diagnostics
            // switches, so a new Boolean setting must fail here rather than be turned off.
            val collectionSwitches =
                settingsConstructor.parameterTypes.indices.filter {
                    settingsConstructor.parameterTypes[it] == Type.Boolean
                }
            if (collectionSwitches.size != 3)
                error("Unsupported Settings constructor: ${settingsConstructor.descriptor}")

            settingsConstructor.before {
                for (switch in collectionSwitches) param(switch).assign(bool(false))
            }
            log.info(
                "Telemetry: ${settingsConstructor.descriptor} keeps usage statistics, crash reports and diagnostics off.",
            )

            // The switches stay off, so the settings screen drops the section that holds them.
            settingsSectionInitializer.after {
                val remaining =
                    call(
                        SettingsSections.without,
                        staticField(settingsSectionEntries),
                        string("Privacy"),
                    )
                setStatic(
                    settingsSectionEntries,
                    newInstance(
                        settingsSectionEntriesConstructor.owner,
                        settingsSectionEntriesConstructor.proto,
                        remaining,
                    ),
                )
            }
            log.info(
                "Telemetry: ${settingsSectionInitializer.descriptor} removes the Privacy section from the settings screen.",
            )

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
                "Telemetry: the manifest turns off Firebase Analytics, Crashlytics, Performance and Sessions.",
            )

            val telemetryConstructors = mapTelemetryConstructors.all
            check(telemetryConstructors.isNotEmpty()) { "Mapbox telemetry constructors not found" }
            for (constructor in telemetryConstructors) {
                constructor.after { thisObject.call(disableTelemetrySession) }
                log.info(
                    "Telemetry: ${constructor.descriptor} turns off Mapbox telemetry collection.",
                )
            }

            setUserTelemetryRequestState.before { param(0).assign(bool(false)) }
            log.info(
                "Telemetry: ${setUserTelemetryRequestState.descriptor} keeps Mapbox telemetry off.",
            )

            shouldSendMapEvents.alwaysReturn(false)
            log.info(
                "Telemetry: ${shouldSendMapEvents.descriptor} drops Mapbox map load and performance events.",
            )

            mapTurnstileEvent.alwaysReturn()
            log.info("Telemetry: ${mapTurnstileEvent.descriptor} drops Mapbox turnstile events.")

            val huaweiEvents = huaweiAnalyticsEvents.all
            check(huaweiEvents.isNotEmpty()) { "Huawei analytics events not found" }
            for (event in huaweiEvents) {
                event.alwaysReturn()
                log.info("Telemetry: ${event.descriptor} drops Huawei analytics events.")
            }
        }
    }
