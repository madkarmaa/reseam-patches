// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.swiftkey.themes

import app.reseam.patch.klass
import app.reseam.patch.method

// Reads files/custom_themes/themelist_custom.json, the themes made with the app's theme editor.
private val readCustomThemeList =
    method("readCustomThemeList") { strings("error while reading custom themes list file") }

// The app's theme model: the settings screens and the keyboard take the installed themes from it.
private val themeModel = klass(readCustomThemeList.owner)

internal val createThemeModel = themeModel.method("<init>")

// Reads every theme list again, the custom one included.
internal val reloadThemeList =
    method("reloadThemeList") {
        inClass(themeModel)
        params()
        calls(readCustomThemeList)
    }
