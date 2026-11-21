// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.liveloop.telemetry

import app.reseam.patch.*
import top.madkarma.patches.liveloop.LiveLoopPatchStrings

// Firebase reads these before the app configures it.
private val firebaseCollection =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "firebase_analytics_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
    )

// Initializes Meta Audience Network at process start.
private const val AUDIENCE_NETWORK_PROVIDER = "com.facebook.ads.AudienceNetworkContentProvider"

private const val APPLOVIN_PRIVACY = "Lcom/applovin/sdk/AppLovinPrivacySettings;"

val disableTelemetry =
    patch(LiveLoopPatchStrings.disableTelemetryName) {
        description(LiveLoopPatchStrings.disableTelemetryDescription)
        compatibleWith("com.wallpaper.liveloop")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Without a consent update, the form never loads and Ad Settings stays hidden.
            requestConsentInfoUpdate.alwaysReturn()
            log.info(
                "Telemetry: ${requestConsentInfoUpdate.descriptor} skips the consent form and Ad Settings.",
            )

            appEntry {
                val context = proto(Type.Void, Type.Boolean, "Landroid/content/Context;")
                callStatic(APPLOVIN_PRIVACY, "setHasUserConsent", context, bool(false), application)
                callStatic(APPLOVIN_PRIVACY, "setDoNotSell", context, bool(true), application)
            }
            log.info("Telemetry: app entry denies AppLovin consent and data sale.")

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

                findByTag("provider")
                    .singleOrNull { it["android:name"] == AUDIENCE_NETWORK_PROVIDER }
                    ?.remove() ?: error("Telemetry: $AUDIENCE_NETWORK_PROVIDER provider missing")
            }
            log.info(
                "Telemetry: the manifest turns off Firebase Analytics, Crashlytics, and Sessions.",
            )
            log.info("Telemetry: the manifest no longer starts Meta Audience Network with the app.")
        }
    }
