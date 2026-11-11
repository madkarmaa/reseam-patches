// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.textra.telemetry

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val fetchAdConfiguration =
    method("fetchAdConfiguration") {
        strings("https://ads%d.textra.me/provider")
    }

internal val fetchTrialLicense =
    method("fetchTrialLicense") {
        strings("https://smsgateway.textra.me/license")
    }

internal val createLoggingBackend =
    klass("com.google.android.datatransport.cct.CctBackendFactory").method("create")

private val telemetryClient = klass("telemetryClient") { strings("ClientTelemetry.API") }

internal val reportClientTelemetry =
    methods("reportClientTelemetry") {
        params(Type.Long, Type.Int, Type.Int, Type.Long)
        calls { owner(telemetryClient.descriptor) }
    }

internal val initializeInMobi =
    method("initializeInMobi") {
        calls {
            owner("com.inmobi.sdk.InMobiSdk")
            name("init")
        }
    }

// All four app adapters accept the same load-state object, unlike the SDKs' own overloads.
internal val initializeAmazon =
    method("initializeAmazon") {
        hasParam(initializeInMobi.parameterTypes.single())
        calls {
            owner("com.amazon.device.ads.AdRegistration")
            name("getInstance")
        }
    }

internal val initializeSmaato =
    method("initializeSmaato") {
        hasParam(initializeInMobi.parameterTypes.single())
        calls {
            owner("com.smaato.sdk.core.SmaatoSdk")
            name("init")
        }
    }

internal val initializeTappx =
    method("initializeTappx") {
        hasParam(initializeInMobi.parameterTypes.single())
        calls {
            owner("com.tappx.sdk.android.TappxBanner")
            name("<init>")
        }
    }

internal val initializeConsent =
    method("initializeConsent") {
        hasParam("java.util.function.Consumer")
        calls {
            owner("com.inmobi.cmp.ChoiceCmp")
            name("startChoice")
        }
    }

private val adDebugLog = klass("adDebugLog") { strings("uuuu-MM-dd'T'HH:mm:ssxxx") }

internal val recordAdDebug =
    method("recordAdDebug") {
        inClass(adDebugLog)
        params("[Ljava/lang/String;")
    }
