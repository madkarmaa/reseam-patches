// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.swiftkey.themes

import app.reseam.patch.*
import top.madkarma.patches.swiftkey.SwiftKeyPatchStrings
import top.madkarma.swiftkey.themes.bindings.InstallThemeActivity
import top.madkarma.swiftkey.themes.bindings.ThemePacks

private const val THEMES_SCREEN = "com.touchtype.materialsettings.themessettings.ThemesActivity"
private const val THEMES_SCREEN_META_DATA = "top.madkarma.swiftkey.themes.THEMES_SCREEN"

// Theme packs have no MIME type of their own: a .rwift file is shared as a generic binary file.
private val THEME_PACK_TYPES =
    listOf("application/zip", "application/x-zip-compressed", "application/octet-stream")

private const val ROW_KEY = "install_theme_pack"

// Material's download icon, tinted like the settings home screen tints its own icons.
private const val ICON = "install_theme_pack"
private const val ICON_PATH = "res/drawable/$ICON.xml"

val installThemePacks =
    patch(SwiftKeyPatchStrings.installThemePacksName) {
        description(SwiftKeyPatchStrings.installThemePacksDescription)
        compatibleWith("com.touchtype.swiftkey")

        execute {
            // The model reads the custom themes once; an installed pack has it read them again.
            createThemeModel.after { call(ThemePacks.rememberThemeModel, thisObject) }
            log.info(
                "Themes: ${createThemeModel.descriptor} hands its theme model to the installer.",
            )

            // The app reads the theme lists again only when its own editor changes them, and the
            // reload method is obfuscated, so the extension's call to it is written here.
            ThemePacks.reloadThemes.implement {
                param(0).cast(reloadThemeList.owner).call(reloadThemeList)
                returnVoid()
            }
            log.info("Themes: the installer reloads themes with ${reloadThemeList.descriptor}.")

            val installer = className(InstallThemeActivity.descriptor)
            manifest.edit {
                if (findByAttribute("android:name", THEMES_SCREEN).isEmpty())
                    error("Themes: the manifest doesn't declare $THEMES_SCREEN")

                val activity = createElement("activity")
                activity["android:name"] = installer
                activity["android:label"] = "Install SwiftKey theme"
                activity["android:theme"] = "@android:style/Theme.Translucent.NoTitleBar"
                activity.setBool("android:exported", true)
                activity.setBool("android:excludeFromRecents", true)

                for (action in listOf("android.intent.action.VIEW", "android.intent.action.SEND")) {
                    val filter = createElement("intent-filter")
                    filter.appendChild(element("action", "android:name" to action))
                    filter.appendChild(
                        element("category", "android:name" to "android.intent.category.DEFAULT"),
                    )

                    for (type in THEME_PACK_TYPES) filter.appendChild(
                        element("data", "android:mimeType" to type),
                    )

                    activity.appendChild(filter)
                }

                activity.appendChild(
                    element(
                        "meta-data",
                        "android:name" to THEMES_SCREEN_META_DATA,
                        "android:value" to THEMES_SCREEN,
                    ),
                )

                findByTag("application").single().appendChild(activity)
            }
            log.info("Themes: $installer installs the theme packs it is opened or shared with.")

            val icon =
                ThemePackResources::class
                    .java
                    .getResourceAsStream("/swiftkey-themes/$ICON.xml")
                    ?.use { it.readBytes() }
                    ?: error("Themes: swiftkey-themes/$ICON.xml is missing from the patch jar")
            resources.addFile("drawable", ICON, ICON_PATH, icon)

            val themesRow =
                resources.id("string", "pref_home_launch_theme_revamp_prefs")
                    ?: error("Themes: pref_home_launch_theme_revamp_prefs string missing")

            val packageName = manifest.packageName ?: error("Themes: the manifest has no package")

            val themesKey = "@0x%08x".format(themesRow.toLong())

            resources.editXml("xml", "prefsx_home_screen") {
                val themes =
                    findByAttribute("android:key", themesKey).singleOrNull()
                        ?: error("Themes: the settings home screen has no Themes row")

                // The home screen handles taps only on its own keys; the row's intent opens this
                // one.
                val install = createElement("Preference")
                install["android:key"] = ROW_KEY
                install["android:title"] = "Install theme pack"
                install["android:summary"] = "Add a .rwift or .zip theme pack from a file"
                install["android:icon"] = "@drawable/$ICON"
                install.appendChild(
                    element(
                        "intent",
                        "android:targetPackage" to packageName,
                        "android:targetClass" to installer,
                    ),
                )

                val screen = themes.parent ?: error("Themes: the Themes row has no screen")
                val rows = screen.children

                val next = rows.getOrNull(rows.indexOfFirst { it["android:key"] == themesKey } + 1)
                if (next == null) screen.appendChild(install)
                else screen.insertBefore(install, next)
            }
            log.info(
                "Themes: the settings home screen offers to install a theme pack below Themes.",
            )
        }
    }

private object ThemePackResources

private fun XmlDocument.element(tag: String, vararg attributes: Pair<String, String>): XmlElement {
    val element = createElement(tag)
    for ((name, value) in attributes) element[name] = value
    return element
}
