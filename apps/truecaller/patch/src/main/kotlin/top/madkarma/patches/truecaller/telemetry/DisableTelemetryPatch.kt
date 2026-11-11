// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.telemetry

import app.reseam.patch.alwaysReturn
import app.reseam.patch.alwaysReturnNull
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.shared.disableFeatureFlag
import top.madkarma.patches.truecaller.shared.featureFlagOverrides
import top.madkarma.truecaller.behavior.bindings.Decisions

val disableTelemetry =
    patch(TruecallerPatchStrings.disableTelemetryName) {
        description(TruecallerPatchStrings.disableTelemetryDescription)
        compatibleWith("com.truecaller")
        dependsOn(featureFlagOverrides)

        execute {
            disableFeatureFlag("featureInternalPerformanceMonitoring_59043")

            for (target in listOf(serializeAnalyticsEvent, cleverTapApi, appsFlyerProvider)) {
                target.alwaysReturnNull()
                log.info("Telemetry: ${target.descriptor} returns null to skip analytics.")
            }

            for (target in listOf(logFirebaseEvent, setFirebaseProperties)) {
                target.alwaysReturn()
                log.info("Telemetry: ${target.descriptor} ignores Firebase analytics calls.")
            }

            uploadAnalyticsBatch.replace { returnValue(Decisions.disabled()) }
            log.info(
                "Telemetry: ${uploadAnalyticsBatch.descriptor} stops queued analytics uploads.",
            )

            manifest.edit {
                val application = findByTag("application").single()

                for ((name, value) in
                    mapOf(
                        "firebase_analytics_collection_deactivated" to "true",
                        "firebase_analytics_collection_enabled" to "false",
                        "firebase_crashlytics_collection_enabled" to "false",
                    )) {
                    val existing = findByTag("meta-data").filter { it["android:name"] == name }
                    if (existing.size > 1) error("Duplicate Firebase metadata: $name")

                    val entry =
                        existing.singleOrNull()
                            ?: createElement("meta-data").also { application.appendChild(it) }

                    entry["android:name"] = name
                    entry["android:value"] = value
                }
            }
        }
    }
