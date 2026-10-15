// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SignatureChecks") @file:Suppress("unused")

package top.madkarma.universal.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Process
import android.util.Base64
import android.util.Log
import app.reseam.runtime.Export
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val TAG = "SignatureKiller"

private class SigningIdentity(val packageName: String, val certificate: ByteArray) {
    val signature = Signature(certificate)
    val digest: ByteArray = MessageDigest.getInstance("SHA-256").digest(certificate)

    fun matches(certificate: ByteArray?, type: Int): Boolean = when (type) {
        PackageManager.CERT_INPUT_RAW_X509 -> this.certificate.contentEquals(certificate)
        PackageManager.CERT_INPUT_SHA256 -> digest.contentEquals(certificate)
        else -> false
    }
}

@Volatile
private var signingIdentity: SigningIdentity? = null

internal fun configureSignatureChecks(packageName: String, signature: String) {
    signingIdentity = SigningIdentity(packageName, Base64.decode(signature, Base64.DEFAULT))
}

@Export
fun hasSigningCertificate(
    manager: PackageManager,
    packageName: String?,
    certificate: ByteArray?,
    type: Int,
): Boolean {
    val identity = signingIdentity

    if (identity != null && packageName == identity.packageName) return identity.matches(
        certificate, type
    )

    return manager.hasSigningCertificate(packageName!!, certificate!!, type)
}

@Export
fun hasSigningCertificateForUid(
    manager: PackageManager,
    uid: Int,
    certificate: ByteArray?,
    type: Int,
): Boolean {
    val identity = signingIdentity

    // Shared UIDs describe several packages; keep Android's signing-history selection there.
    if (identity != null && uid == Process.myUid() && manager.getPackagesForUid(uid)
            ?.singleOrNull() == identity.packageName
    ) return identity.matches(certificate, type)

    return manager.hasSigningCertificate(uid, certificate!!, type)
}

@Export
fun getPackageArchiveInfo(
    manager: PackageManager,
    archiveFilePath: String,
    flags: Int,
): PackageInfo? = spoofInstalledArchive(
    manager,
    archiveFilePath,
    manager.getPackageArchiveInfo(archiveFilePath, flags),
)

internal fun spoofInstalledArchive(
    manager: PackageManager,
    archiveFilePath: String?,
    info: PackageInfo?,
): PackageInfo? {
    val identity = signingIdentity ?: return info

    if (info == null || info.packageName != identity.packageName || archiveFilePath == null) return info

    try {
        val application = manager.getApplicationInfo(identity.packageName, 0)
        val archive = File(archiveFilePath).canonicalFile

        val installedPaths = listOfNotNull(
            application.sourceDir, application.publicSourceDir
        ) + application.splitSourceDirs.orEmpty() + application.splitPublicSourceDirs.orEmpty()

        if (installedPaths.none { File(it).canonicalFile == archive }) return info
    } catch (exception: PackageManager.NameNotFoundException) {
        return info
    } catch (exception: IOException) {
        Log.w(TAG, "Cannot resolve installed APK path", exception)
        return info
    } catch (exception: SecurityException) {
        Log.w(TAG, "Cannot inspect installed APK path", exception)
        return info
    }

    replaceFirstSignature(getLegacySignatures(info), identity.signature)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) replaceFirstSignature(
        info.signingInfo?.apkContentsSigners, identity.signature
    )

    return info
}

/** Opt-in startup diagnostics. Direct framework calls deliberately show the unredirected results. */
@Suppress("DEPRECATION")
@Export
fun checkSignatureApis(context: Context) {
    val identity = signingIdentity ?: return
    val manager = context.packageManager

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val raw = PackageManager.CERT_INPUT_RAW_X509
            val sha256 = PackageManager.CERT_INPUT_SHA256

            Log.i(
                TAG, "hasSigningCertificate probe: systemRaw=" + manager.hasSigningCertificate(
                    identity.packageName, identity.certificate, raw
                ) + " systemSha256=" + manager.hasSigningCertificate(
                    identity.packageName, identity.digest, sha256
                ) + " redirectedRaw=" + hasSigningCertificate(
                    manager, identity.packageName, identity.certificate, raw
                ) + " redirectedSha256=" + hasSigningCertificate(
                    manager, identity.packageName, identity.digest, sha256
                ) + " redirectedUidRaw=" + hasSigningCertificateForUid(
                    manager, Process.myUid(), identity.certificate, raw
                ) + " redirectedUidSha256=" + hasSigningCertificateForUid(
                    manager, Process.myUid(), identity.digest, sha256
                )
            )
        }

        val flags =
            PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val path = context.applicationInfo.sourceDir
        val systemInfo = manager.getPackageArchiveInfo(path, flags)

        // The probe already parsed the APK, so use the same post-processing as the redirects.
        val systemCertificate = archiveCertificate(systemInfo)
        val redirected = spoofInstalledArchive(manager, path, systemInfo)

        Log.i(
            TAG, "installed APK probe: parsed=${systemInfo != null}" + " systemOriginal=${
                identity.certificate.contentEquals(systemCertificate)
            }" + " redirectedOriginal=${
                identity.certificate.contentEquals(
                    archiveCertificate(
                        redirected
                    )
                )
            }" + " redirectedAcceptsInstalled=" + hasSigningCertificate(
                manager, identity.packageName, systemCertificate, PackageManager.CERT_INPUT_RAW_X509
            )
        )
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Signature API diagnostics failed", exception)
    }
}

private fun archiveCertificate(info: PackageInfo?): ByteArray? {
    if (info == null) return null

    val signatures =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners else getLegacySignatures(
            info
        )

    return signatures?.firstOrNull()?.toByteArray()
}
