// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.signature

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private const val COPY_BUFFER_SIZE = 102400

internal fun extractApkEntry(zip: ZipFile, assetPath: String, context: Context): File {
    val entry = zip.getEntry(assetPath) ?: throw IOException("Entry not found: $assetPath")

    // The CRC also changes the cache name when an update replaces an equally sized component.
    val directory = context.getDir(LIBRARY_NAME, Context.MODE_PRIVATE)

    val replacement = File(directory, "${entry.crc}-${File(assetPath).name}")
    return extractZipEntry(zip, entry, replacement)
}

internal fun extractZipEntry(zip: ZipFile, entry: ZipEntry, replacement: File): File {
    if (replacement.isFile && replacement.length() == entry.size) {
        logDebug { "Reusing extracted ${entry.name} at $replacement (${entry.size} bytes)" }
        return replacement
    }

    val started = SystemClock.elapsedRealtime()
    val temporary = File.createTempFile("extract-", ".tmp", replacement.parentFile)

    try {
        zip.getInputStream(entry).use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        }

        if (!temporary.renameTo(replacement)) throw IOException("Cannot replace $replacement")
    } finally {
        temporary.delete()
    }

    logInfo {
        "Extracted ${entry.name} to $replacement (${entry.size} bytes, crc " +
            "${
            java.lang.Long.toHexString(
                entry.crc,
            )
        }) in ${SystemClock.elapsedRealtime() - started} ms"
    }
    return replacement
}
