// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.signature

import android.content.Context
import java.util.zip.ZipFile

private const val ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk"
private const val SPLIT_ASSET_DIRECTORY = "assets/SignatureKiller/splits"
internal var fileRedirectionInstalled = false
internal var libcFileHooksEnabled = true

internal fun installApkFileAccess(context: Context, packageName: String) {
    // The native hooks also cover libraries loaded after installation.
    if (fileRedirectionInstalled) {
        logDebug { "killOpen: file redirection is already installed in this process" }
        return
    }

    try {
        loadSignatureLibrary(context, libcFileHooksEnabled)
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
        "killOpen: installed base ${application.sourceDir}, splits " +
            "${splitNames.contentToString()} at ${splitPaths.contentToString()}"
    }
    check(splitNames.size == splitPaths.size) { "Installed split names and paths differ in size" }

    for (index in splitNames.indices) {
        installedPaths.add(splitPaths[index])
        assetPaths.add("$SPLIT_ASSET_DIRECTORY/${splitNames[index]}.apk")
    }

    // Extract everything before installing the hooks, which redirect later reads of the base too.
    val replacements =
        ZipFile(application.sourceDir).use { zip ->
            assetPaths.map { extractApkEntry(zip, it, context).absolutePath }
        }

    check(
        hookApkPaths(
            installedPaths.toTypedArray(),
            replacements.toTypedArray(),
            libcFileHooksEnabled,
        ),
    ) {
        "Installing original APK file redirection failed"
    }

    fileRedirectionInstalled = true

    for (index in installedPaths.indices) logInfo {
        "killOpen: hooking ${installedPaths[index]} -> ${replacements[index]}"
    }
}
