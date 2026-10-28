// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("GmsCoreSupport") @file:Suppress("unused")

package top.madkarma.yazio.gms

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.credentials.Credential
import android.credentials.GetCredentialResponse
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import app.reseam.runtime.Export

private const val TAG = "YazioGmsCore"
private const val CREDENTIAL_RESPONSE = "android.service.credentials.extra.GET_CREDENTIAL_RESPONSE"
private const val CREDENTIAL_TYPE = "androidx.credentials.provider.extra.EXTRA_CREDENTIAL_TYPE"
private const val CREDENTIAL_DATA = "androidx.credentials.provider.extra.EXTRA_CREDENTIAL_DATA"

// Credential Manager requests Google ID tokens through the identity credentials service.
private fun isAuthentication(action: String): Boolean =
    action.startsWith("com.google.android.gms.auth.") || action == "com.google.android.gms.signin.service.START" || action == "com.google.android.gms.identitycredentials.service.START"

@Export
fun servicePackageOverride(action: String, vendor: String): String? {
    if (!isAuthentication(action)) return null

    Log.i(TAG, "Routing $action through $vendor.android.gms")
    return "$vendor.android.gms"
}

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

@Suppress("UseKtx")
@Export
fun check(activity: Activity, packageName: String) {
    try {
        activity.packageManager.getPackageInfo(packageName, 0)
        Log.i(TAG, "GmsCore is installed: $packageName")
    } catch (_: PackageManager.NameNotFoundException) {
        AlertDialog.Builder(activity).setTitle("GmsCore is not installed")
            .setMessage("Yazio needs GmsCore ($packageName) for Google sign-in. Install it and add your Google account there.")
            .setPositiveButton("Get GmsCore") { _, _ ->
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://reseam.app/download/#gmscore")
                    )
                )
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
