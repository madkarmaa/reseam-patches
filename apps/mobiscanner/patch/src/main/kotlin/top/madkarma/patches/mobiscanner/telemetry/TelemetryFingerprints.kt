// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mobiscanner.telemetry

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// Banner, interstitial and native ads all ask this check first; the purchase writers that share
// the string return nothing, and the app-open check takes parameters.
internal val adsAllowed =
    method("adsAllowed") {
        strings("KEY_IN_APP_PURCHASED")
        returns(Type.Boolean)
        params()
    }

// App-open ads skip the shared check and decide on their own here.
internal val showAppOpenAd =
    method("showAppOpenAd") {
        inClass(klass("com.mobisystems.monetization.appopenads.AppOpenAdsManager"))
        strings("app_open_type")
    }

internal val showConsentForm = method("showConsentForm") { strings("Showing consent") }

// Amplitude, Clarity and CleverTap each start only when their Remote Config switch, read through
// this getter, is on.
internal val remoteConfigFlag =
    method("remoteConfigFlag") {
        calledBy(method("startClarity") { strings("clarity_sdk_enabled") })
        params(Type.String)
        returns(Type.Boolean)
    }

// Every app event passes this name check on its way to Firebase Analytics and Amplitude.
internal val logEvent = method("logEvent") { strings("^[a-zA-Z][a-zA-Z0-9_]{0,39}\$") }

private val appsFlyerSdk = klass("appsFlyerSdk") { extends("com.appsflyer.AppsFlyerLib") }

// Fetches AppsFlyer's remote configuration and registers the app with the SDK.
internal val appsFlyerInit = appsFlyerSdk.method("init")

// Launch attribution, in-app events, and the push token used for uninstall tracking.
private val appsFlyerReportNames = setOf("start", "logEvent", "updateServerUninstallToken")

internal val appsFlyerReports =
    methods("appsFlyerReports") {
        inClass(appsFlyerSdk)
        custom { name in appsFlyerReportNames }
    }

// Reports the install to Facebook even with automatic app events off.
internal val publishFacebookInstall =
    klass("com.facebook.FacebookSdk").method("publishInstallAsync")
