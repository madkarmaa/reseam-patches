// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.signature

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

internal const val LIBRARY_NAME = "SignatureKiller"

/** Load directly, or keep the extension outside the installed APK's native library inventory. */
internal fun loadSignatureLibrary(context: Context, direct: Boolean) {
    if (direct) {
        System.loadLibrary(LIBRARY_NAME)
        return
    }

    val abi =
        when (val directory = File(context.applicationInfo.nativeLibraryDir).name) {
            "arm64" -> "arm64-v8a"
            "arm" -> "armeabi-v7a"
            else -> error("Unsupported native library directory: $directory")
        }

    ZipFile(context.applicationInfo.sourceDir).use { zip ->
        val library = "libSignatureKiller.so"
        val entry =
            zip.getEntry("lib/$abi/$library") ?: error("Native library is missing: $library")
        val directory =
            File(context.getDir(LIBRARY_NAME, Context.MODE_PRIVATE), "native-${entry.crc}")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val extracted = extractZipEntry(zip, entry, File(directory, library))
        check(extracted.setReadOnly()) { "Cannot make $extracted read-only" }
        System.load(extracted.absolutePath)
    }
}
