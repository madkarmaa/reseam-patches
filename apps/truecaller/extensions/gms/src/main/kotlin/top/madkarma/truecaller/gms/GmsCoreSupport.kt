// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("GmsCoreSupport")
@file:Suppress("unused")

package top.madkarma.truecaller.gms

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import app.reseam.runtime.Export
import top.madkarma.shared.gmscore.serviceAction
import top.madkarma.shared.gmscore.servicePackageOverride
import top.madkarma.shared.gmscore.vendorGroupId

@Export
fun requiresPlayServices(required: Boolean, action: String?): Boolean =
    required && servicePackageOverride(action) == null

@Export
fun check(context: Context?, originalResult: Int): Int {
    if (originalResult == 0 || context == null) return originalResult
    return try {
        val packageName = "${vendorGroupId()}.android.gms"
        val service = context.packageManager.getApplicationInfo(packageName, 0)

        if (!service.enabled) return originalResult

        Log.i("TruecallerGms", "Found an enabled GmsCore app")
        0
    } catch (_: PackageManager.NameNotFoundException) {
        originalResult
    }
}

@Export
fun endpointPackage(original: String?, action: String?): String? =
    servicePackageOverride(action) ?: original

@Export
fun dynamicLookup(original: Boolean, action: String?): Boolean =
    original && servicePackageOverride(action) == null

@Export
fun endpointAction(action: String): String =
    if (servicePackageOverride(action) == null) action else serviceAction(action)
