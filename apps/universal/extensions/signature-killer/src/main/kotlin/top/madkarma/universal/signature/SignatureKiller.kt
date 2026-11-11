// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SignatureKiller")
@file:Suppress("unused")

package top.madkarma.universal.signature

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import app.reseam.runtime.Export
import java.io.File
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Prepares original APK files without redirecting the Android runtime's own file opens. */
@Export
fun initializeBeforeNativeLibrary(
    packageName: String,
    signature: String,
    libraryName: String,
    diagnostics: Boolean,
) {
    initializeNativeFileAccess(packageName, signature, diagnostics)
    redirectApkFilesOnLibraryLoad(libraryName)
}

/**
 * Prepares original files for a native hook installed explicitly after the target library loads.
 */
@Export
fun initializeNativeFileAccess(packageName: String, signature: String, diagnostics: Boolean) {
    check(!fileRedirectionInstalled) { "Native file access must be configured before file hooks" }
    libcFileHooksEnabled = false
    initializeEarly(packageName, signature, true, diagnostics)
}

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
        "Initializing in attachBaseContext for $packageName in ${processDescription()}; " +
            "APK file access ${if (spoofApkPath) "on" else "off"}"
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
        "Initializing before application startup for $packageName in " +
            "${processDescription()}; APK file access ${if (spoofApkPath) "on" else "off"}"
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
        "killSignature: $packageName spoofs certificate ${certificateFingerprint(certificate)} " +
            "(${certificate.size} bytes)"
    }
    configureSignatureChecks(packageName, base64Sig)
    installPackageSignatures(packageName, base64Sig)
}

@Export
fun killApkPath(context: Context, packageName: String) {
    // The library directory names the process ABI; the device may prefer another one.
    logInfo {
        "killApkPath: $packageName on ${File(context.applicationInfo.nativeLibraryDir).name}"
    }
    killOpen(context, packageName)
}

/** Traps raw file-open syscalls from a loaded library and redirects installed APK paths. */
@Export
@Synchronized
fun redirectRawApkOpens(libraryName: String) {
    check(fileRedirectionInstalled) { "Original APK file redirection is not installed" }
    check(trapRawApkOpens(libraryName, !libcFileHooksEnabled)) {
        "Installing raw APK open redirection failed in $libraryName"
    }
    logInfo { "redirectRawApkOpens: seccomp redirect installed for $libraryName" }
}

/** Arms APK file redirection after a library loads and before Android calls its JNI_OnLoad. */
@Export
@Synchronized
fun redirectApkFilesOnLibraryLoad(libraryName: String) {
    check(fileRedirectionInstalled) { "Original APK file redirection is not installed" }
    check(prepareApkFilesOnLibraryLoad(libraryName, !libcFileHooksEnabled)) {
        "Preparing APK file redirection failed for $libraryName"
    }
    logInfo { "redirectApkFilesOnLibraryLoad: waiting for $libraryName" }
}

/** Compares Android's signature APIs with the spoofed certificate when diagnostics are enabled. */
@Export
fun checkSignatures(context: Context, packageName: String, base64Sig: String) =
    logSignatures(context, packageName, base64Sig)

// Preserve the same monitor as the other synchronized exported redirection methods.
@Synchronized
private fun killOpen(context: Context, packageName: String) =
    installApkFileAccess(context, packageName)

internal external fun setNativeDiagnostics(enabled: Boolean)

internal external fun hookApkPaths(
    installedPaths: Array<String>,
    replacementPaths: Array<String>,
    hookLibc: Boolean,
): Boolean

private external fun trapRawApkOpens(libraryName: String, redirectContents: Boolean): Boolean

private external fun prepareApkFilesOnLibraryLoad(
    libraryName: String,
    redirectContents: Boolean,
): Boolean
