// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ThemePacks")

package top.madkarma.swiftkey.themes

import android.content.Context
import android.net.Uri
import app.reseam.runtime.Export
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// A theme pack, as Rwiftkey and the community share them (.rwift or .zip): one folder per theme,
// named by its id, and the themelist_custom.json that lists them. SwiftKey keeps the themes made
// with its own editor in the same shape, in files/custom_themes.
private const val CUSTOM_THEMES = "custom_themes"
private const val THEME_LIST = "themelist_custom.json"
private const val STAGING = ".installing"

@Volatile private var themeModel: Any? = null

@Export
fun rememberThemeModel(model: Any?) {
    themeModel = model
}

// Reads every theme list again, the custom one included.
@Export external fun reloadThemes(model: Any?)

internal class InvalidThemePackException(message: String) : IOException(message)

/** Installs the pack's themes over any with the same id, and returns their names. */
internal fun installThemePack(context: Context, pack: Uri): List<String> {
    val customThemes = File(context.filesDir, CUSTOM_THEMES)

    val staging = File(customThemes, STAGING)
    staging.deleteRecursively()

    try {
        unpack(context, pack, staging)

        val packList = File(staging, THEME_LIST)
        if (!packList.isFile) throw InvalidThemePackException("The file is not a theme pack")

        val themes = readThemes(packList)
        if (themes.isEmpty()) throw InvalidThemePackException("The theme pack lists no themes")

        val ids = themes.map { it.getString("id") }
        for (id in ids) {
            val folder = File(staging, id)

            if (id.startsWith(".") || folder.parentFile != staging)
                throw InvalidThemePackException("The theme pack has an invalid theme id: $id")
            if (!File(folder, "style.json").isFile)
                throw InvalidThemePackException("The theme pack has no style.json for $id")
        }

        for (id in ids) {
            val installed = File(customThemes, id)
            installed.deleteRecursively()

            if (!File(staging, id).renameTo(installed))
                throw IOException("Cannot move theme $id into place")
        }

        val themeList = File(customThemes, THEME_LIST)
        val kept = readThemes(themeList).filterNot { it.getString("id") in ids }
        writeThemes(themeList, kept + themes)

        themeModel?.let { reloadThemes(it) }
        return themes.map { it.optString("name", it.getString("id")) }
    } finally {
        staging.deleteRecursively()
    }
}

private fun unpack(context: Context, pack: Uri, destination: File) {
    val root = destination.canonicalFile
    val input =
        context.contentResolver.openInputStream(pack) ?: throw IOException("Cannot open $pack")

    ZipInputStream(input.buffered()).use { zip ->
        var found = false

        generateSequence { zip.nextEntry }
            .forEach { entry ->
                found = true

                val file = File(root, entry.name).canonicalFile
                if (!file.path.startsWith(root.path + File.separator))
                    throw InvalidThemePackException(
                        "The theme pack has an unsafe path: ${entry.name}",
                    )

                if (entry.isDirectory) {
                    file.mkdirs()
                    return@forEach
                }

                file.parentFile?.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
            }

        if (!found) throw InvalidThemePackException("The file is not a theme pack")
    }
}

private fun readThemes(list: File): List<JSONObject> {
    if (!list.isFile) return emptyList()

    try {
        val themes = JSONObject(list.readText()).optJSONArray("themes") ?: return emptyList()

        return (0 until themes.length()).map {
            val theme = themes.getJSONObject(it)

            if (theme.optString("id").isEmpty())
                throw InvalidThemePackException("${list.name} lists a theme without an id")

            theme
        }
    } catch (exception: JSONException) {
        throw InvalidThemePackException("Invalid ${list.name}: ${exception.message}")
    }
}

private fun writeThemes(list: File, themes: List<JSONObject>) {
    val part = File(list.parentFile, "${list.name}.part")
    part.writeText(JSONObject().put("themes", JSONArray(themes)).toString())
    if (!part.renameTo(list)) throw IOException("Cannot write ${list.name}")
}
