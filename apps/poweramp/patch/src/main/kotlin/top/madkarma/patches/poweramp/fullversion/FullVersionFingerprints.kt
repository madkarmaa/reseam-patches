// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.poweramp.fullversion

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

// JNI registration retains the bridge class and its native method names.
internal val licenseBridge = klass("com.maxmpz.audioplayer.Sync")

internal val initializeNativeLicense =
    method("initializeNativeLicense") {
        calls(licenseBridge.method("native_process"))
    }

private val licensePreferences =
    klass("licensePreferences") {
        strings("getSbuf()Ljava/nio/ByteBuffer;")
    }

internal val initializeLicensePreferences = licensePreferences.method("<clinit>")

private val canRestorePurchase =
    method("canRestorePurchase") {
        inClass(klass("com.maxmpz.audioplayer.preference.BaseRestoreFvPurchasePref"))
        returns(Type.Boolean)
    }

internal val fullVersionState =
    canRestorePurchase.field("fullVersionState") {
        owner(licensePreferences.descriptor)
    }

private val storeLicenseResponse =
    method("storeLicenseResponse") {
        strings("pending_end_ts")
    }

// The response handler stores "pending_end_ts" beside a boolean license preference.
internal val pendingLicenseExpiry =
    storeLicenseResponse.field("pendingLicenseExpiry") {
        owner(licensePreferences.descriptor)
        type(fullVersionState.type)
    }

private val integerPreference =
    klass("integerPreference") {
        strings("IntPref=")
    }

private val setPreferenceValue =
    method("setPreferenceValue") {
        inClass(integerPreference, inherited = true)
        params(Type.Object)
    }

// The boxed-value setter delegates to this primitive integer setter.
internal val setIntegerPreference =
    method("setIntegerPreference") {
        calledBy(setPreferenceValue)
        params(Type.Int)
    }

private val beginLicenseCall =
    method("beginLicenseCall") {
        inClass(licenseBridge)
        calls(licenseBridge.method("native_acquire"))
    }

// The license status screen reads its labels from the bridge under the "ps" tag, which the
// extension answers.
internal val readLicenseStatus =
    method("readLicenseStatus") {
        strings("ps")
        calls(beginLicenseCall)
    }

// Activity launches go through the native library under the "sac" tag. A few days after the trial
// ends, it silently drops them.
internal val launchActivity =
    method("launchActivity") {
        strings("sac")
        calls(beginLicenseCall)
    }

// The app's message bus is the one Handler implementing MsgBus. The native library posts its
// license verdicts to it.
private val messageBus =
    klass("messageBus") {
        implements("Lcom/maxmpz/widget/MsgBus;")
        extends("Landroid/os/Handler;")
    }

internal val postMessage = messageBus.method("post")

// The purchased card joins two labels into "One time purchase | Lifetime license".
internal val updatePurchaseCard =
    klass("com.maxmpz.audioplayer.preference.BaseBuyFvPref").method("update")
