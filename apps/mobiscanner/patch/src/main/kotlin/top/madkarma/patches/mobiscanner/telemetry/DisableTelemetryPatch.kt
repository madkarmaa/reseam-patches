// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mobiscanner.telemetry

import app.reseam.patch.*
import top.madkarma.mobiscanner.telemetry.bindings.Telemetry
import top.madkarma.patches.mobiscanner.MobiScannerPatchStrings
import top.madkarma.patches.mobiscanner.shared.mobiScannerIntegrity

// Firebase and the Facebook SDK read these before the app configures them.
private val collectionSwitches =
    mapOf(
        "firebase_analytics_collection_deactivated" to "true",
        "google_analytics_adid_collection_enabled" to "false",
        "firebase_crashlytics_collection_enabled" to "false",
        "firebase_sessions_enabled" to "false",
        "com.facebook.sdk.AutoLogAppEventsEnabled" to "false",
        "com.facebook.sdk.AdvertiserIDCollectionEnabled" to "false",
    )

// These start ad networks with the app process, before any ad is requested.
private val adNetworkStarters =
    listOf(
        "com.applovin.sdk.AppLovinInitProvider",
        "com.inmobi.sdk.InMobiInitProvider",
        "com.vungle.ads.VungleProvider",
        "com.facebook.ads.AudienceNetworkContentProvider",
    )

val disableTelemetry =
    patch(MobiScannerPatchStrings.disableTelemetryName) {
        description(MobiScannerPatchStrings.disableTelemetryDescription)
        compatibleWith("com.mobisystems.mobiscanner")
        dependsOn(mobiScannerIntegrity)

        execute {
            adsAllowed.alwaysReturn(false)
            log.info(
                "Telemetry: ${adsAllowed.descriptor} blocks banner, interstitial and native ads.",
            )

            showAppOpenAd.alwaysReturn(false)
            log.info("Telemetry: ${showAppOpenAd.descriptor} never shows app-open ads.")

            showConsentForm.alwaysReturn()
            log.info("Telemetry: ${showConsentForm.descriptor} skips the ad consent form.")

            remoteConfigFlag.before {
                whenTrue(Telemetry.isTrackerSwitch(param(0))) { returnFalse() }
            }
            log.info(
                "Telemetry: ${remoteConfigFlag.descriptor} keeps Amplitude, Clarity and CleverTap off.",
            )

            logEvent.alwaysReturn()
            log.info("Telemetry: ${logEvent.descriptor} drops every app event.")

            appsFlyerInit.replace { returnValue(thisObject) }
            log.info("Telemetry: ${appsFlyerInit.descriptor} leaves AppsFlyer unconfigured.")

            val appsFlyerReports = appsFlyerReports.all
            if (appsFlyerReports.isEmpty()) error("Telemetry: AppsFlyer report methods missing")
            for (report in appsFlyerReports) {
                report.alwaysReturn()
                log.info("Telemetry: ${report.descriptor} sends nothing to AppsFlyer.")
            }

            publishFacebookInstall.alwaysReturn()
            log.info(
                "Telemetry: ${publishFacebookInstall.descriptor} never reports the install to Facebook.",
            )

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

                for (provider in adNetworkStarters) findByTag("provider")
                    .singleOrNull { it["android:name"] == provider }
                    ?.remove() ?: error("Telemetry: $provider provider missing")
            }
            log.info(
                "Telemetry: the manifest turns off Firebase Analytics, Crashlytics, Sessions, " +
                    "and Facebook app events.",
            )
            log.info(
                "Telemetry: the manifest no longer starts AppLovin, InMobi, Vungle and Meta Audience Network with the app.",
            )
        }
    }
