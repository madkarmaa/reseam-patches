// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SignatureLogging")

package top.madkarma.universal.signature

import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

internal const val TAG = "SignatureKiller"

/** Set from the patch's diagnostics option. When off, the extension and native library log nothing. */
@Volatile
internal var diagnosticsEnabled = false

internal inline fun logDebug(message: () -> String) {
    if (diagnosticsEnabled) Log.d(TAG, message())
}

internal inline fun logInfo(message: () -> String) {
    if (diagnosticsEnabled) Log.i(TAG, message())
}

internal inline fun logWarning(exception: Throwable? = null, message: () -> String) {
    if (diagnosticsEnabled) Log.w(TAG, message(), exception)
}

internal inline fun logError(exception: Throwable? = null, message: () -> String) {
    if (diagnosticsEnabled) Log.e(TAG, message(), exception)
}

/** Logs only the first [limit] events of a frequent kind, so hot paths cannot flood logcat. */
internal class EventLog(private val kind: String, private val limit: Int = 16) {
    private val count = AtomicInteger()

    fun log(message: () -> String) {
        if (!diagnosticsEnabled) return

        val number = count.incrementAndGet()
        if (number == limit + 1) Log.d(TAG, "$kind: later events are not logged")
        if (number > limit) return

        Log.d(TAG, "$kind #$number: ${message()}")
    }
}

/** The first 16 hex digits of a certificate's SHA-256, matching the patcher's log. */
internal fun certificateFingerprint(certificate: ByteArray?): String {
    if (certificate == null) return "none"

    return hexPrefix(MessageDigest.getInstance("SHA-256").digest(certificate))
}

internal fun hexPrefix(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }.take(16)

/** Describes the running process, since multi-process apps initialize once per process. */
internal fun processDescription(): String {
    val name = try {
        File("/proc/self/cmdline").readText().substringBefore('\u0000')
    } catch (exception: IOException) {
        "unknown"
    }

    return "process $name (pid ${Process.myPid()}, uid ${Process.myUid()}), API ${Build.VERSION.SDK_INT}"
}
