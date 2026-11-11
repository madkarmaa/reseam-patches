// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SmsVerification")
@file:Suppress("unused")

package top.madkarma.truecaller.verification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.reseam.runtime.Export
import com.truecaller.wizard.verification.otp.sms.OtpSmsApi

private var application: Context? = null
private var permissionRequested = false

@Export
fun start(context: Context?) {
    application = context?.applicationContext
}

@Export
fun prepare(activity: Activity?) {
    if (activity == null || activity.isFinishing || permissionRequested) return
    if (
        activity.checkSelfPermission(Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED
    )
        return

    permissionRequested = true
    try {
        activity.requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 0x6f74)
    } catch (exception: RuntimeException) {
        Log.w(
            "TruecallerSms",
            "SMS permission request failed. The app will use Google SMS retrieval.",
            exception,
        )
    }
}

@Export
fun api(): Int {
    val context = application
    val granted =
        context?.checkSelfPermission(Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    Log.i(
        "TruecallerSms",
        if (granted) "Using Android's SMS receiver"
        else "No SMS permission. Using Google SMS retrieval",
    )
    return (if (granted) OtpSmsApi.SMS else OtpSmsApi.GOOGLE).value
}

@Export
fun overrideApi(key: String?, original: Int): Int =
    if (key == "verificationOtpSmsApi_19731") api() else original
