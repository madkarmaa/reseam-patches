@file:JvmName("SignatureKiller") @file:Suppress("unused")

package bin.mt.signature

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
import java.io.*
import java.lang.reflect.Field
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipFile

private const val TAG = "SignatureKiller"
private const val LIBRARY_NAME = "SignatureKiller"
private const val ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk"
private const val ORIGIN_FILE_NAME = "origin.apk"
private const val SELF_MAPS_PATH = "/proc/self/maps"
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

private fun killOpen(context: Context, packageName: String) {
    try {
        System.loadLibrary(LIBRARY_NAME)
    } catch (exception: UnsatisfiedLinkError) {
        Log.e(TAG, "Load SignatureKiller library failed", exception)
        return
    } catch (exception: RuntimeException) {
        Log.e(TAG, "Load SignatureKiller library failed", exception)
        return
    }
    Log.i(TAG, "killOpen: native library loaded")
    val apkPath = getApkPath(packageName)
    if (apkPath == null) {
        Log.e(TAG, "Get apk path failed")
        return
    }
    Log.i(TAG, "killOpen: installed apk at $apkPath")
    val apkFile = File(apkPath)
    val replacement = try {
        extractOriginApk(apkFile, context)
    } catch (exception: IOException) {
        throw RuntimeException(exception)
    } ?: return
    Log.i(
        TAG,
        "killOpen: hooking ${apkFile.absolutePath} -> ${replacement.absolutePath} (${replacement.length()} bytes)",
    )
    hookApkPath(apkFile.absolutePath, replacement.absolutePath)
}

private fun extractOriginApk(apkFile: File, context: Context): File? {
    val replacement = File(context.dataDir, ORIGIN_FILE_NAME)
    ZipFile(apkFile).use { zip ->
        val entry = zip.getEntry(ORIGIN_ASSET_PATH)
        if (entry == null) {
            Log.e(TAG, "Entry not found: $ORIGIN_ASSET_PATH")
            return null
        }
        if (!replacement.exists() || replacement.length() != entry.size) zip.getInputStream(entry)
            .use { input ->
                FileOutputStream(replacement).use { output ->
                    input.copyTo(output, COPY_BUFFER_SIZE)
                }
            }
    }
    return replacement
}

private fun getApkPath(packageName: String): String? {
    try {
        BufferedReader(FileReader(SELF_MAPS_PATH)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: return null
                val path = line.split(Regex("\\s+")).last()
                if (isApkPath(packageName, path)) return path
            }
        }
    } catch (exception: IOException) {
        throw RuntimeException(exception)
    } catch (exception: RuntimeException) {
        throw RuntimeException(exception)
    }
}

private fun isApkPath(packageName: String, path: String): Boolean {
    if (!path.startsWith('/') || !path.endsWith(".apk")) return false
    val segments = path.substring(1).split('/', limit = 6)
    return when (segments.size) {
        4, 5 -> isInstalledBaseApk(packageName, segments) || isAsecPackageApk(
            packageName,
            segments,
        )

        3 -> isLegacyAppApk(packageName, segments)
        6 -> isAdoptedStorageApk(packageName, segments)
        else -> false
    }
}

private fun isInstalledBaseApk(packageName: String, segments: List<String>): Boolean =
    segments[0] == "data" && segments[1] == "app" && segments.last() == "base.apk" && segments[segments.size - 2].startsWith(
        packageName
    )

private fun isAsecPackageApk(packageName: String, segments: List<String>): Boolean =
    segments[0] == "mnt" && segments[1] == "asec" && segments.last() == "pkg.apk" && segments[segments.size - 2].startsWith(
        packageName
    )

private fun isLegacyAppApk(packageName: String, segments: List<String>): Boolean =
    segments[0] == "data" && segments[1] == "app" && segments[2].startsWith(packageName)

private fun isAdoptedStorageApk(packageName: String, segments: List<String>): Boolean =
    segments[0] == "mnt" && segments[1] == "expand" && segments[3] == "app" && segments[5] == "base.apk" && segments[4].endsWith(
        packageName
    )

private external fun hookApkPath(apkPath: String, replacementPath: String)
