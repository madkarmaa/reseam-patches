// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.signature

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.util.Base64
import java.lang.reflect.Field
import org.lsposed.hiddenapibypass.HiddenApiBypass

private val packageInfoSpoofs = EventLog("PackageInfo spoof")

internal fun installPackageSignatures(packageName: String, signatureData: String) {
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
        val exempted =
            HiddenApiBypass.addHiddenApiExemptions(
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        info.signingInfo?.apkContentsSigners
                    else null
                replaceFirstSignature(signers, fakeSignature)

                // Absent arrays usually mean the caller did not request signature flags.
                packageInfoSpoofs.log {
                    "$packageName legacy signatures ${signatureState(legacySignatures)}, " +
                        "signing info ${
                        signatureState(
                            signers,
                        )
                    }"
                }
            }

            return info
        }

        override fun newArray(size: Int): Array<PackageInfo?> = originalCreator.newArray(size)
    }
}

internal fun signatureState(signatures: Array<Signature>?): String =
    when {
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
