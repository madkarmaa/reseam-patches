// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.radarbot.telemetry

import app.reseam.patch.*
import top.madkarma.patches.radarbot.RadarbotPatchStrings
import top.madkarma.patches.radarbot.shared.radarbotIntegrity

// Firebase reads these before the app configures it, and the deactivation cannot be undone at
// runtime.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
    )

val disableTelemetry =
    patch(RadarbotPatchStrings.disableTelemetryName) {
        description(RadarbotPatchStrings.disableTelemetryDescription)
        compatibleWith("com.vialsoft.radarbot_free")
        dependsOn(radarbotIntegrity)

        execute {
            // Refuse everything in place of showing the notice, so Didomi and the ad SDKs reading
            // its consent string record the refusal.
            userStatusRequired.after {
                whenTrue(result) {
                    thisObject.call(refuseAll)
                    returnFalse()
                }
            }
            log.info(
                "Telemetry: ${userStatusRequired.descriptor} refuses all consent instead of showing the notice.",
            )

            for (target in listOf(purposeConsent, vendorConsent)) {
                target.alwaysReturn(false)
                log.info("Telemetry: ${target.descriptor} reports the consent as refused.")
            }

            appsFlyerInit.replace { returnValue(thisObject) }
            log.info("Telemetry: ${appsFlyerInit.descriptor} leaves AppsFlyer uninitialized.")

            val appsFlyerReports = appsFlyerReports.flatMap { it.all }
            check(appsFlyerReports.isNotEmpty()) { "Telemetry: AppsFlyer reports not found" }
            for (target in appsFlyerReports) {
                target.alwaysReturn()
                log.info("Telemetry: ${target.descriptor} no longer reports to AppsFlyer.")
            }

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
