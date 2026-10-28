// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("GmsCoreSupport") @file:Suppress("unused")

package top.madkarma.liftoff.gms

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.reseam.runtime.Export

private const val TAG = "LiftoffGmsCore"

private fun isAuthentication(action: String): Boolean =
    action.startsWith("com.google.android.gms.auth.") || action == "com.google.android.gms.signin.service.START"

@Export
fun servicePackageOverride(action: String, vendor: String): String? {
    if (!isAuthentication(action)) return null

    Log.i(TAG, "Routing $action through $vendor.android.gms")
    return "$vendor.android.gms"
}

@Suppress("UseKtx")
@Export
fun check(activity: Activity, packageName: String) {
    try {
        activity.packageManager.getPackageInfo(packageName, 0)
        Log.i(TAG, "GmsCore is installed: $packageName")
    } catch (_: PackageManager.NameNotFoundException) {
        AlertDialog.Builder(activity).setTitle("GmsCore is not installed")
            .setMessage("Liftoff needs GmsCore ($packageName) for Google sign-in. Install it and add your Google account there.")
            .setPositiveButton("Get GmsCore") { _, _ ->
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW, Uri.parse("https://reseam.app/download/#gmscore")
                    )
                )
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
}
