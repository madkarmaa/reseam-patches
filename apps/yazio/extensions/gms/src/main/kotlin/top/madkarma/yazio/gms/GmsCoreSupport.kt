// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("GmsCoreSupport") @file:Suppress("unused")

package top.madkarma.yazio.gms

import android.content.Intent
import android.credentials.Credential
import android.credentials.GetCredentialResponse
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.reseam.runtime.Export

private const val TAG = "YazioGmsCore"
private const val CREDENTIAL_RESPONSE = "android.service.credentials.extra.GET_CREDENTIAL_RESPONSE"
private const val CREDENTIAL_TYPE = "androidx.credentials.provider.extra.EXTRA_CREDENTIAL_TYPE"
private const val CREDENTIAL_DATA = "androidx.credentials.provider.extra.EXTRA_CREDENTIAL_DATA"

// GmsCore returns the pre-Android 14 Bundle form, but AndroidX reads a framework response on Android 14+.
@Export
fun restoreFrameworkCredentialResponse(intent: Intent) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return

    @Suppress("DEPRECATION") val response =
        intent.extras?.get(CREDENTIAL_RESPONSE) as? Bundle ?: return
    val type = response.getString(CREDENTIAL_TYPE) ?: return
    val data = response.getBundle(CREDENTIAL_DATA) ?: return

    intent.putExtra(CREDENTIAL_RESPONSE, GetCredentialResponse(Credential(type, data)))
    Log.i(TAG, "Converted the GmsCore $type response for Android 14+")
}
