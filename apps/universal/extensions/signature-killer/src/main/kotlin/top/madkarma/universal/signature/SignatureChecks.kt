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
import app.reseam.runtime.Export
import java.io.File
import java.io.IOException
import java.security.MessageDigest

private val certificateChecks = EventLog("hasSigningCertificate")
private val archiveReads = EventLog("getPackageArchiveInfo")

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
    val previous = signingIdentity
    signingIdentity = SigningIdentity(packageName, Base64.decode(signature, Base64.DEFAULT))
    logDebug {
        "Signature checks answer for $packageName" + if (previous != null) " (replacing an earlier configuration)" else ""
    }
}

private fun describeCertificate(certificate: ByteArray?, type: Int): String = when (type) {
    PackageManager.CERT_INPUT_RAW_X509 -> "raw certificate ${certificateFingerprint(certificate)}"
    PackageManager.CERT_INPUT_SHA256 -> "SHA-256 ${certificate?.let(::hexPrefix) ?: "none"}"
    else -> "unknown certificate type $type"
}

@Export
fun hasSigningCertificate(
    manager: PackageManager,
    packageName: String?,
    certificate: ByteArray?,
    type: Int,
): Boolean {
    val identity = signingIdentity

    if (identity != null && packageName == identity.packageName) {
        val matches = identity.matches(certificate, type)
        certificateChecks.log {
            "$packageName with ${describeCertificate(certificate, type)} -> $matches (spoofed)"
        }
        return matches
    }

    certificateChecks.log { "$packageName is not the patched package; asking Android" }
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

    if (identity == null || uid != Process.myUid()) {
        certificateChecks.log { "uid $uid is not this app; asking Android" }
        return manager.hasSigningCertificate(uid, certificate!!, type)
    }

    // Shared UIDs describe several packages; keep Android's signing-history selection there.
    val packages = manager.getPackagesForUid(uid)
    if (packages?.singleOrNull() != identity.packageName) {
        certificateChecks.log {
            "uid $uid is shared by ${packages.contentToString()}; asking Android"
        }
        return manager.hasSigningCertificate(uid, certificate!!, type)
    }

    val matches = identity.matches(certificate, type)
    certificateChecks.log {
        "uid $uid with ${describeCertificate(certificate, type)} -> $matches (spoofed)"
    }
    return matches
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

    if (info == null || info.packageName != identity.packageName || archiveFilePath == null) {
        archiveReads.log {
            "$archiveFilePath parsed as ${info?.packageName ?: "nothing"}; left unchanged"
        }
        return info
    }

    try {
        val application = manager.getApplicationInfo(identity.packageName, 0)
        val archive = File(archiveFilePath).canonicalFile

        val installedPaths = listOfNotNull(
            application.sourceDir, application.publicSourceDir
        ) + application.splitSourceDirs.orEmpty() + application.splitPublicSourceDirs.orEmpty()

        if (installedPaths.none { File(it).canonicalFile == archive }) {
            archiveReads.log { "$archive is not an installed component; left unchanged" }
            return info
        }
    } catch (exception: PackageManager.NameNotFoundException) {
        archiveReads.log { "${identity.packageName} is not installed; $archiveFilePath left unchanged" }
        return info
    } catch (exception: IOException) {
        logWarning(exception) { "Cannot resolve installed APK path $archiveFilePath" }
        return info
    } catch (exception: SecurityException) {
        logWarning(exception) { "Cannot inspect installed APK path $archiveFilePath" }
        return info
    }

    val legacySignatures = getLegacySignatures(info)
    replaceFirstSignature(legacySignatures, identity.signature)

    val signers =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners
        else null
    replaceFirstSignature(signers, identity.signature)

    archiveReads.log {
        "$archiveFilePath: legacy signatures ${signatureState(legacySignatures)}, " + "signing info ${
            signatureState(
                signers
            )
        }"
    }
    return info
}

/** Opt-in startup diagnostics. Direct framework calls deliberately show the unredirected results. */
@Suppress("DEPRECATION")
@Export
fun checkSignatureApis(context: Context) {
    if (!diagnosticsEnabled) return
    val identity = signingIdentity ?: return
    val manager = context.packageManager

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val raw = PackageManager.CERT_INPUT_RAW_X509
            val sha256 = PackageManager.CERT_INPUT_SHA256

            logInfo {
                "hasSigningCertificate probe: systemRaw=" + manager.hasSigningCertificate(
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
            }
        }

        val flags =
            PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val path = context.applicationInfo.sourceDir
        val systemInfo = manager.getPackageArchiveInfo(path, flags)

        // The probe already parsed the APK, so use the same post-processing as the redirects.
        val systemCertificate = archiveCertificate(systemInfo)
        val redirected = spoofInstalledArchive(manager, path, systemInfo)

        logInfo {
            "installed APK probe: parsed=${systemInfo != null}" + " systemOriginal=${
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
        }
    } catch (exception: RuntimeException) {
        logWarning(exception) { "Signature API diagnostics failed" }
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
