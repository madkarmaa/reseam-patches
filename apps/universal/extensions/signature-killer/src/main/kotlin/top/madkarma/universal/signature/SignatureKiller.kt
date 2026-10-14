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
import android.util.Base64
import android.util.Log
import app.reseam.runtime.Export
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.reflect.Field
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipFile

private const val TAG = "SignatureKiller"
private const val LIBRARY_NAME = "SignatureKiller"
private const val ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk"
private const val SPLIT_ASSET_DIRECTORY = "assets/SignatureKiller/splits"
private var fileRedirectionInstalled = false
private const val COPY_BUFFER_SIZE = 102400

/**
 * Spoofs package signatures and optionally redirects installed-file reads to the embedded original.
 */
@Export
fun initialize(
    context: Context,
    packageName: String,
    signature: String,
    spoofApkPath: Boolean,
) {
    Log.i(TAG, "Initializing in attachBaseContext")
    checkSignatures(context, packageName, signature)
    killSignature(packageName, signature)
    checkSignatures(context, packageName, signature)
    if (spoofApkPath) killApkPath(context, packageName)
}

@Export
@Suppress("PrivateApi")
fun initializeEarly(packageName: String, signature: String, spoofApkPath: Boolean) {
    killSignature(packageName, signature)
    if (!spoofApkPath) return
    try {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/app/ActivityThread;")

        val activityThread = Class.forName("android.app.ActivityThread")
        val thread = activityThread.getMethod("currentActivityThread").invoke(null)
        val systemContext = activityThread.getMethod("getSystemContext").invoke(thread) as Context

        val context =
            systemContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)

        killApkPath(context, packageName)
    } catch (exception: ReflectiveOperationException) {
        Log.e(TAG, "Early APK-path spoof failed", exception)
    } catch (exception: PackageManager.NameNotFoundException) {
        Log.e(TAG, "Early APK-path spoof failed", exception)
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Early APK-path spoof failed", exception)
    }
}

@Export
fun killSignature(packageName: String, base64Sig: String) {
    Log.i(TAG, "killSignature: $packageName (sig ${base64Sig.length} chars)")
    killPM(packageName, base64Sig)
}

@Export
fun killApkPath(context: Context, packageName: String) {
    Log.i(TAG, "killApkPath: $packageName on ${Build.SUPPORTED_ABIS[0]}")
    killOpen(context, packageName)
}

@Export
fun checkSignatures(context: Context, packageName: String, base64Sig: String) {
    val real = try {
        val current = currentSignatures(context, packageName)
        if (!current.isNullOrEmpty()) shortSig(current[0].toByteArray()) else "none"
    } catch (exception: PackageManager.NameNotFoundException) {
        "unavailable ($exception)"
    } catch (exception: RuntimeException) {
        "unavailable ($exception)"
    }

    val spoofed = shortSig(Base64.decode(base64Sig, Base64.DEFAULT))

    Log.i(
        TAG,
        "checkSignatures: $packageName real=$real spoofed=$spoofed" + if (real == spoofed) " SAME" else " DIFFERENT",
    )
    Log.i(TAG, "checkSignatures: $packageName history=${historySig(context, packageName)}")
}

private fun historySig(context: Context, packageName: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return "n/a (pre-P)"
    try {
        val info = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )

        val history = info.signingInfo?.signingCertificateHistory
        return if (!history.isNullOrEmpty()) shortSig(history[0].toByteArray()) + " x" + history.size
        else "none"
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

private fun shortSig(certificate: ByteArray): String {
    try {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate)
        val hex = StringBuilder()

        for (byte in digest) {
            hex.append(Character.forDigit((byte.toInt() shr 4) and 0xF, 16))
            hex.append(Character.forDigit(byte.toInt() and 0xF, 16))
        }

        return hex.substring(0, 16)
    } catch (exception: NoSuchAlgorithmException) {
        return "unavailable"
    }
}

private fun killPM(packageName: String, signatureData: String) {
    val fakeSignature = Signature(Base64.decode(signatureData, Base64.DEFAULT))
    val creator = getCreator(packageName, fakeSignature)

    try {
        findField(PackageInfo::class.java, "CREATOR").set(null, creator)
    } catch (exception: ReflectiveOperationException) {
        throw RuntimeException(exception)
    } catch (exception: RuntimeException) {
        throw RuntimeException(exception)
    }

    Log.i(TAG, "killPM: PackageInfo creator spoof installed")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions(
        "Landroid/os/Parcel;",
        "Landroid/content/pm",
        "Landroid/app",
    )

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
                replaceFirstSignature(getLegacySignatures(info), fakeSignature)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) replaceFirstSignature(
                    info.signingInfo?.apkContentsSigners,
                    fakeSignature,
                )
            }

            return info
        }

        override fun newArray(size: Int): Array<PackageInfo?> = originalCreator.newArray(size)
    }
}

@Suppress("UNCHECKED_CAST")
private fun getLegacySignatures(info: PackageInfo): Array<Signature>? {
    try {
        return findField(PackageInfo::class.java, "signatures").get(info) as Array<Signature>?
    } catch (exception: ReflectiveOperationException) {
        Log.w(TAG, "Reading legacy signatures failed", exception)
        return null
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Reading legacy signatures failed", exception)
        return null
    }
}

private fun replaceFirstSignature(signatures: Array<Signature>?, fakeSignature: Signature) {
    if (!signatures.isNullOrEmpty()) signatures[0] = fakeSignature
}

private fun clearQuietly(what: String, action: () -> Unit) {
    try {
        action()
    } catch (exception: ReflectiveOperationException) {
        Log.w(TAG, "Clearing $what failed", exception)
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Clearing $what failed", exception)
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
    if (fileRedirectionInstalled) {
        check(refreshApkPaths()) { "Refreshing original APK file redirection failed" }
        return
    }
    try {
        System.loadLibrary(LIBRARY_NAME)
    } catch (exception: UnsatisfiedLinkError) {
        Log.e(TAG, "Load SignatureKiller library failed", exception)
        return
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Load SignatureKiller library failed", exception)
        return
    }

    val info = context.packageManager.getPackageInfo(packageName, 0)
    val application = checkNotNull(info.applicationInfo) { "Application info is unavailable" }

    val installedPaths = mutableListOf(application.sourceDir)
    val assetPaths = mutableListOf(ORIGIN_ASSET_PATH)

    val splitNames = info.splitNames
    val splitPaths = application.splitSourceDirs ?: emptyArray()
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

    for (index in installedPaths.indices) Log.i(
        TAG, "killOpen: hooking ${installedPaths[index]} -> ${replacements[index]}"
    )
}

private fun extractOriginApk(zip: ZipFile, assetPath: String, context: Context): File {
    val entry = zip.getEntry(assetPath) ?: throw IOException("Entry not found: $assetPath")

    // The CRC also changes the cache name when an update replaces an equally sized component.
    val directory = context.getDir(LIBRARY_NAME, Context.MODE_PRIVATE)

    val replacement = File(directory, "${entry.crc}-${File(assetPath).name}")
    if (replacement.isFile && replacement.length() == entry.size) return replacement

    val temporary = File.createTempFile("origin-", ".apk", directory)

    try {
        zip.getInputStream(entry).use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        }

        if (!temporary.renameTo(replacement)) throw IOException("Cannot replace $replacement")
    } finally {
        temporary.delete()
    }

    return replacement
}

private external fun hookApkPaths(
    installedPaths: Array<String>, replacementPaths: Array<String>
): Boolean

private external fun refreshApkPaths(): Boolean
