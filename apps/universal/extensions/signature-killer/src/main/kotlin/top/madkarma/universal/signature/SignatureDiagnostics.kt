// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.signature

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Base64

/** Compares what Android reports with the spoofed certificate. Runs only with diagnostics on. */
internal fun logSignatures(context: Context, packageName: String, base64Sig: String) {
    if (!diagnosticsEnabled) return

    val real =
        try {
            val current = currentSignatures(context, packageName)
            if (!current.isNullOrEmpty()) certificateFingerprint(current[0].toByteArray())
            else "none"
        } catch (exception: PackageManager.NameNotFoundException) {
            "unavailable ($exception)"
        } catch (exception: RuntimeException) {
            "unavailable ($exception)"
        }

    val spoofed = certificateFingerprint(Base64.decode(base64Sig, Base64.DEFAULT))

    logInfo {
        "checkSignatures: $packageName real=$real spoofed=$spoofed" +
            if (real == spoofed) " SAME" else " DIFFERENT"
    }
    logInfo { "checkSignatures: $packageName history=${historySig(context, packageName)}" }
}

private fun historySig(context: Context, packageName: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a (pre-P)"
    try {
        val info =
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )

        val history = info.signingInfo?.signingCertificateHistory
        return if (!history.isNullOrEmpty()) {
            certificateFingerprint(history[0].toByteArray()) + " x" + history.size
        } else "none"
    } catch (exception: PackageManager.NameNotFoundException) {
        return "unavailable ($exception)"
    } catch (exception: RuntimeException) {
        return "unavailable ($exception)"
    }
}

@Suppress("DEPRECATION")
private fun currentSignatures(context: Context, packageName: String): Array<Signature>? {
    val manager = context.packageManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        return manager
            .getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            .signingInfo
            ?.apkContentsSigners

    return getLegacySignatures(manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES))
}
