// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SignatureKiller") @file:Suppress("unused")

package top.madkarma.universal.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.Base64
import app.reseam.runtime.Export
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.util.zip.ZipFile

private const val LIBRARY_NAME = "SignatureKiller"
private const val ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk"
private const val SPLIT_ASSET_DIRECTORY = "assets/SignatureKiller/splits"
private var fileRedirectionInstalled = false
private const val COPY_BUFFER_SIZE = 102400

private val packageInfoSpoofs = EventLog("PackageInfo spoof")

/**
 * Spoofs package signatures and optionally redirects installed-file reads to the embedded original.
 */
@Export
fun initialize(
    context: Context,
    packageName: String,
    signature: String,
    spoofApkPath: Boolean,
    diagnostics: Boolean,
) {
    diagnosticsEnabled = diagnostics
    logInfo {
        "Initializing in attachBaseContext for $packageName in ${processDescription()}; " + "APK file access ${if (spoofApkPath) "on" else "off"}"
    }
    checkSignatures(context, packageName, signature)
    killSignature(packageName, signature)
    checkSignatures(context, packageName, signature)
    if (spoofApkPath) killApkPath(context, packageName)
    if (diagnostics) checkSignatureApis(context)
}

@Export
@Suppress("PrivateApi")
fun initializeEarly(
    packageName: String,
    signature: String,
    spoofApkPath: Boolean,
    diagnostics: Boolean,
) {
    diagnosticsEnabled = diagnostics
    logInfo {
        "Initializing early from the Pairip startup launcher for $packageName in " + "${processDescription()}; APK file access ${if (spoofApkPath) "on" else "off"}"
    }
    killSignature(packageName, signature)
    if (!spoofApkPath) return
    try {
        val exempted = HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityThread;")
        logDebug { "Hidden API exemption for ActivityThread: $exempted" }

        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("currentActivityThread").invoke(null)
        val systemContext = activityThread.getMethod("getSystemContext").invoke(thread) as Context

        val context =
            systemContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
        logDebug { "Created an early package context from the system context" }

        killApkPath(context, packageName)
    } catch (exception: ReflectiveOperationException) {
        logError(exception) { "Early APK-path spoof failed" }
    } catch (exception: PackageManager.NameNotFoundException) {
        logError(exception) { "Early APK-path spoof failed" }
    } catch (exception: RuntimeException) {
        logError(exception) { "Early APK-path spoof failed" }
    }
}

@Export
fun killSignature(packageName: String, base64Sig: String) {
    logInfo {
        val certificate = Base64.decode(base64Sig, Base64.DEFAULT)
        "killSignature: $packageName spoofs certificate ${certificateFingerprint(certificate)} " + "(${certificate.size} bytes)"
    }
    configureSignatureChecks(packageName, base64Sig)
    killPM(packageName, base64Sig)
}

@Export
fun killApkPath(context: Context, packageName: String) {
    // The library directory names the process ABI; the device may prefer another one.
    logInfo { "killApkPath: $packageName on ${File(context.applicationInfo.nativeLibraryDir).name}" }
    killOpen(context, packageName)
}

/** Traps raw openat syscalls from a loaded library and redirects installed APK paths. */
@Export
@Synchronized
fun redirectRawApkOpens(libraryName: String) {
    check(fileRedirectionInstalled) { "Original APK file redirection is not installed" }
    check(trapRawApkOpens(libraryName)) {
        "Installing raw APK open redirection failed in $libraryName"
    }
    logInfo { "redirectRawApkOpens: seccomp redirect installed for $libraryName" }
}

/** Compares what Android reports with the spoofed certificate. Runs only with diagnostics on. */
@Export
fun checkSignatures(context: Context, packageName: String, base64Sig: String) {
    if (!diagnosticsEnabled) return

    val real = try {
        val current = currentSignatures(context, packageName)
        if (!current.isNullOrEmpty()) certificateFingerprint(current[0].toByteArray()) else "none"
    } catch (exception: PackageManager.NameNotFoundException) {
        "unavailable ($exception)"
    } catch (exception: RuntimeException) {
        "unavailable ($exception)"
    }

    val spoofed = certificateFingerprint(Base64.decode(base64Sig, Base64.DEFAULT))

    logInfo {
        "checkSignatures: $packageName real=$real spoofed=$spoofed" + if (real == spoofed) " SAME" else " DIFFERENT"
    }
    logInfo { "checkSignatures: $packageName history=${historySig(context, packageName)}" }
}

private fun historySig(context: Context, packageName: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a (pre-P)"
    try {
        val info = context.packageManager.getPackageInfo(
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return manager.getPackageInfo(
        packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
    ).signingInfo?.apkContentsSigners

    return getLegacySignatures(manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES))
}

private fun killPM(packageName: String, signatureData: String) {
    val fakeSignature = Signature(Base64.decode(signatureData, Base64.DEFAULT))
    // Another hooker, or an earlier initialization in this process, may already wrap the creator.
    logDebug { "killPM: wrapping PackageInfo.CREATOR ${PackageInfo.CREATOR.javaClass.name}" }
    val creator = getCreator(packageName, fakeSignature)

    try {
        findField(PackageInfo::class.java, "CREATOR").set(null, creator)
    } catch (exception: ReflectiveOperationException) {
        throw RuntimeException(exception)
    } catch (exception: RuntimeException) {
        throw RuntimeException(exception)
    }

    logInfo { "killPM: PackageInfo creator spoof installed" }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val exempted = HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/os/Parcel;",
            "Landroid/content/pm",
            "Landroid/app",
        )
        logDebug { "killPM: hidden API exemptions for Parcel and package caches: $exempted" }
    }

    clearQuietly("package info cache") {
        val cache =
            checkNotNull(findField(PackageManager::class.java, "sPackageInfoCache").get(null)) {
                "Package info cache is unavailable"
            }
        cache.javaClass.getMethod("clear").invoke(cache)
    }

    clearQuietly("parcel creator cache") {
        val creators = findField(Parcel::class.java, "mCreators").get(null) as MutableMap<*, *>
        creators.clear()
    }

    clearQuietly("parcel paired-creator cache") {
        val creators =
            findField(Parcel::class.java, "sPairedCreators").get(null) as MutableMap<*, *>
        creators.clear()
    }
}

private fun getCreator(
    packageName: String,
    fakeSignature: Signature,
): Parcelable.Creator<PackageInfo> {
    val originalCreator = PackageInfo.CREATOR
    return object : Parcelable.Creator<PackageInfo> {
        override fun createFromParcel(source: Parcel): PackageInfo {
            val info = originalCreator.createFromParcel(source)

            if (info.packageName == packageName) {
                val legacySignatures = getLegacySignatures(info)
                replaceFirstSignature(legacySignatures, fakeSignature)

                val signers =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo?.apkContentsSigners
                    else null
                replaceFirstSignature(signers, fakeSignature)

                // Absent arrays usually mean the caller did not request signature flags.
                packageInfoSpoofs.log {
                    "$packageName legacy signatures ${signatureState(legacySignatures)}, " + "signing info ${
                        signatureState(
                            signers
                        )
                    }"
                }
            }

            return info
        }

        override fun newArray(size: Int): Array<PackageInfo?> = originalCreator.newArray(size)
    }
}

internal fun signatureState(signatures: Array<Signature>?): String = when {
    signatures == null -> "absent"
    signatures.isEmpty() -> "empty"
    else -> "replaced (first of ${signatures.size})"
}

@Suppress("UNCHECKED_CAST")
internal fun getLegacySignatures(info: PackageInfo): Array<Signature>? {
    try {
        return findField(PackageInfo::class.java, "signatures").get(info) as Array<Signature>?
    } catch (exception: ReflectiveOperationException) {
        logWarning(exception) { "Reading legacy signatures failed" }
        return null
    } catch (exception: RuntimeException) {
        logWarning(exception) { "Reading legacy signatures failed" }
        return null
    }
}

internal fun replaceFirstSignature(signatures: Array<Signature>?, fakeSignature: Signature) {
    if (!signatures.isNullOrEmpty()) signatures[0] = fakeSignature
}

private fun clearQuietly(what: String, action: () -> Unit) {
    try {
        action()
        logDebug { "Cleared $what" }
    } catch (exception: ReflectiveOperationException) {
        logWarning(exception) { "Clearing $what failed" }
    } catch (exception: RuntimeException) {
        logWarning(exception) { "Clearing $what failed" }
    }
}

private fun findField(start: Class<*>, fieldName: String): Field {
    var missing: NoSuchFieldException? = null
    var current: Class<*>? = start

    while (current != null && current != Any::class.java) {
        try {
            val field = current.getDeclaredField(fieldName)
            field.isAccessible = true
            return field
        } catch (exception: NoSuchFieldException) {
            if (missing == null) missing = exception
        }

        current = current.superclass
    }

    throw missing ?: NoSuchFieldException(fieldName)
}

@Synchronized
private fun killOpen(context: Context, packageName: String) {
    // The native hooks also cover libraries loaded after installation.
    if (fileRedirectionInstalled) {
        logDebug { "killOpen: file redirection is already installed in this process" }
        return
    }

    try {
        System.loadLibrary(LIBRARY_NAME)
    } catch (exception: UnsatisfiedLinkError) {
        logError(exception) { "Load SignatureKiller library failed" }
        return
    } catch (exception: RuntimeException) {
        logError(exception) { "Load SignatureKiller library failed" }
        return
    }
    setNativeDiagnostics(diagnosticsEnabled)

    val info = context.packageManager.getPackageInfo(packageName, 0)
    val application = checkNotNull(info.applicationInfo) { "Application info is unavailable" }

    val installedPaths = mutableListOf(application.sourceDir)
    val assetPaths = mutableListOf(ORIGIN_ASSET_PATH)

    val splitNames = info.splitNames
    val splitPaths = application.splitSourceDirs ?: emptyArray()
    logDebug {
        "killOpen: installed base ${application.sourceDir}, splits " + "${splitNames.contentToString()} at ${splitPaths.contentToString()}"
    }
    check(splitNames.size == splitPaths.size) { "Installed split names and paths differ in size" }

    for (index in splitNames.indices) {
        installedPaths.add(splitPaths[index])
        assetPaths.add("$SPLIT_ASSET_DIRECTORY/${splitNames[index]}.apk")
    }

    // Extract everything before installing the hooks, which redirect later reads of the base too.
    val replacements = ZipFile(application.sourceDir).use { zip ->
        assetPaths.map { extractOriginApk(zip, it, context).absolutePath }
    }

    check(hookApkPaths(installedPaths.toTypedArray(), replacements.toTypedArray())) {
        "Installing original APK file redirection failed"
    }

    fileRedirectionInstalled = true

    for (index in installedPaths.indices) logInfo {
        "killOpen: hooking ${installedPaths[index]} -> ${replacements[index]}"
    }
}

private fun extractOriginApk(zip: ZipFile, assetPath: String, context: Context): File {
    val entry = zip.getEntry(assetPath) ?: throw IOException("Entry not found: $assetPath")

    // The CRC also changes the cache name when an update replaces an equally sized component.
    val directory = context.getDir(LIBRARY_NAME, Context.MODE_PRIVATE)

    val replacement = File(directory, "${entry.crc}-${File(assetPath).name}")
    if (replacement.isFile && replacement.length() == entry.size) {
        logDebug { "Reusing extracted $assetPath at $replacement (${entry.size} bytes)" }
        return replacement
    }

    val started = SystemClock.elapsedRealtime()
    val temporary = File.createTempFile("origin-", ".apk", directory)

    try {
        zip.getInputStream(entry).use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        }

        if (!temporary.renameTo(replacement)) throw IOException("Cannot replace $replacement")
    } finally {
        temporary.delete()
    }

    logInfo {
        "Extracted $assetPath to $replacement (${entry.size} bytes, crc " + "${
            java.lang.Long.toHexString(
                entry.crc
            )
        }) in ${SystemClock.elapsedRealtime() - started} ms"
    }
    return replacement
}

private external fun setNativeDiagnostics(enabled: Boolean)

private external fun hookApkPaths(
    installedPaths: Array<String>, replacementPaths: Array<String>
): Boolean

private external fun trapRawApkOpens(libraryName: String): Boolean
