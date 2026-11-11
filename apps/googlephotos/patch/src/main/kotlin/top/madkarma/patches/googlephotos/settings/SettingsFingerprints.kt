// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.googlephotos.settings

import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

internal val settingsItem = klass("settingsItem") { strings("SettingsItemData(headlineText=") }
internal val settingsItemConstructor = settingsItem.method("<init>")

private val settingsMenu = klass("settingsMenu") { strings("SettingsMenuData(items=") }
internal val settingsMenuConstructor = settingsMenu.method("<init>")
internal val settingsMenuItems = settingsMenu.fieldOfType("Ljava/util/List;")

// Nested screens show bare menus; only the main page wraps its menu in a page state.
internal val settingsPageConstructor =
    klass("settingsPage") { strings("SettingsPageUiState(menuData=") }
        .method("<init>") {
            hasParam(settingsMenu.descriptor)
        }

// Nested screens such as Preferences route their rows through their own destination enum.
internal val nestedSettingsDestination =
    klass("nestedSettingsDestination") {
        enumConstants("GridControls", "NearDupes", "Memories")
    }

// The main screen's navigation, which only ever receives its own destination enum.
internal val settingsNavigation = method("settingsNavigation") { strings("Unknown activity class") }
