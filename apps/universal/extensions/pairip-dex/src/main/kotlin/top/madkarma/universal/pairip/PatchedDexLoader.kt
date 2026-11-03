// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.universal.pairip

import android.content.Context
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream

internal fun loadPatchedDex(context: Context, parent: ClassLoader?): ClassLoader {
    val classes = context.assets.open("reseam/pairip/classes.txt").bufferedReader()
        .use { it.readLines().toSet() }
    check(classes.isNotEmpty()) { "Patched Pairip DEX has no class inventory" }

    val file = File.createTempFile("reseam-pairip-", ".dex", context.codeCacheDir)
    try {
        FileOutputStream(file).use { output ->
            check(file.setReadOnly()) { "Cannot make patched DEX read-only" }
            context.assets.open("reseam/pairip/patched.dex").use { it.copyTo(output) }
        }

        return PatchedDexLoader(file.path, context.codeCacheDir.path, parent, classes)
    } finally {
        file.delete()
    }
}

private class PatchedDexLoader(
    path: String,
    cache: String,
    parent: ClassLoader?,
    private val patchedNames: Set<String>,
) : DexClassLoader(path, cache, null, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        if (name !in patchedNames) return@synchronized super.loadClass(name, resolve)

        val definition = findLoadedClass(name) ?: findClass(name)
        if (resolve) resolveClass(definition)
        definition
    }
}
