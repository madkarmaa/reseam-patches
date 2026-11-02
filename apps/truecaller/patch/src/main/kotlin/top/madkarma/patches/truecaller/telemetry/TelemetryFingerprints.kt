// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.telemetry

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

internal val serializeAnalyticsEvent = method("serializeAnalyticsEvent") {
    strings("unauthenticated")
}

private val firebaseAnalytics = klass("firebaseAnalyticsAdapter") {
    strings(" exceeds max length: 40 characters")
}

internal val logFirebaseEvent = method("logFirebaseEvent") {
    inClass(firebaseAnalytics)
    params("android.os.Bundle", Type.String)
}

internal val setFirebaseProperties = method("setFirebaseProperties") {
    inClass(firebaseAnalytics)
    params("java.util.Map")
}

private val cleverTapLogin =
    method("cleverTapLogin") { strings("_onUserLogin"); returns(Type.Object) }

internal val cleverTapApi = method("cleverTapApi") {
    inClass(klass(cleverTapLogin.owner))
    calledBy(cleverTapLogin)
}

internal val appsFlyerProvider = method("appsFlyerProvider") {
    calls { owner("com.appsflyer.AppsFlyerLib"); name("init") }
}

private val scheduleAnalyticsBatch = method("scheduleAnalyticsBatch") {
    strings("featureImmediateAnalyticsBatchUpload_43907")
    returns(Type.Object)
}

internal val uploadAnalyticsBatch = method("uploadAnalyticsBatch") {
    inClass(klass(scheduleAnalyticsBatch.owner))
    calledBy(scheduleAnalyticsBatch)
    hasParam(Type.Boolean)
}
