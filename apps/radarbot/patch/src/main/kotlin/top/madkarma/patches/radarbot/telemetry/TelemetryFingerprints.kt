// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.radarbot.telemetry

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// The app asks Didomi whether the user accepted a purpose such as data selling or geolocation data.
// Outside consent regulations it treats every purpose as accepted.
internal val purposeConsent =
    method("purposeConsent") {
        calls {
            owner("io.didomi.sdk.models.UserStatus\$Purposes")
            name("getGlobal")
        }
    }

// The same holder answers for single vendors: Google Analytics, AppsFlyer, Braze, RevenueCat, and
// Intercom.
internal val vendorConsent =
    method("vendorConsent") {
        inClass(klass(purposeConsent.owner))
        calls {
            owner("io.didomi.sdk.models.CurrentUserStatus")
            name("getVendors")
        }
    }

private val didomi = klass("io.didomi.sdk.Didomi")

// The app shows the consent notice while Didomi still needs the user's choice.
internal val userStatusRequired = didomi.method("shouldUserStatusBeCollected")
internal val refuseAll = didomi.method("setUserDisagreeToAll")

private val appsFlyer = klass("appsFlyer") { extends("com.appsflyer.AppsFlyerLib") }

// Initialization registers activity callbacks that sample motion sensors, fetch AppsFlyer's remote
// configuration, and request deferred deep links whether or not the user consented.
internal val appsFlyerInit = appsFlyer.method("init")

// The app starts install attribution and logs events to AppsFlyer regardless of consent.

internal val appsFlyerReports =
    listOf("start", "logEvent").map { name ->
        methods("appsFlyer${name.replaceFirstChar(Char::uppercase)}") {
            inClass(appsFlyer)
            name(name)
        }
    }
