// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.poweramp.fullversion

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

// JNI registration retains the bridge class and its native method names.
internal val licenseBridge = klass("com.maxmpz.audioplayer.Sync")

internal val initializeNativeLicense = method("initializeNativeLicense") {
    calls(licenseBridge.method("native_process"))
}

private val licensePreferences = klass("licensePreferences") {
    strings("getSbuf()Ljava/nio/ByteBuffer;")
}

internal val initializeLicensePreferences = licensePreferences.method("<clinit>")

private val canRestorePurchase = method("canRestorePurchase") {
    inClass(klass("com.maxmpz.audioplayer.preference.BaseRestoreFvPurchasePref"))
    returns(Type.Boolean)
}

internal val fullVersionState = canRestorePurchase.field("fullVersionState") {
    owner(licensePreferences.descriptor)
}

private val storeLicenseResponse = method("storeLicenseResponse") {
    strings("pending_end_ts")
}

// The response handler stores "pending_end_ts" beside a boolean license preference.
internal val pendingLicenseExpiry = storeLicenseResponse.field("pendingLicenseExpiry") {
    owner(licensePreferences.descriptor)
    type(fullVersionState.type)
}

private val integerPreference = klass("integerPreference") {
    strings("IntPref=")
}

private val setPreferenceValue = method("setPreferenceValue") {
    inClass(integerPreference, inherited = true)
    params(Type.Object)
}

// The boxed-value setter delegates to this primitive integer setter.
internal val setIntegerPreference = method("setIntegerPreference") {
    calledBy(setPreferenceValue)
    params(Type.Int)
}

// The purchased card joins two labels into "One time purchase | Lifetime license".
internal val updatePurchaseCard =
    klass("com.maxmpz.audioplayer.preference.BaseBuyFvPref").method("update")
