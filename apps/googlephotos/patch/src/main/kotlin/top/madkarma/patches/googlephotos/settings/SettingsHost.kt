// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.googlephotos.settings

import app.reseam.patch.appEntry
import app.reseam.patch.before
import app.reseam.patch.enumConstant
import app.reseam.patch.settings.openSettings
import app.reseam.patch.settings.settingsHost
import top.madkarma.googlephotos.settings.entry.bindings.SettingsEntry
import top.madkarma.patches.googlephotos.GOOGLE_PHOTOS

private const val ICON = "reseam_settings_icon"
private const val INTEGER = "Ljava/lang/Integer;"

// A nested destination never reaches the main screen's navigation, so it marks the Reseam row there.
private const val REUSED_DESTINATION = "GridControls"

val googlePhotosSettings = settingsHost("googlephotos") {
    compatibleWith(GOOGLE_PHOTOS)

    install {
        val icon = SettingsResources::class.java.getResourceAsStream("/reseam-settings/$ICON.xml")
            ?.use { it.readBytes() }
            ?: error("reseam-settings/$ICON.xml is missing from the patch jar")

        val iconId = resources.addFile("drawable", ICON, "res/drawable/$ICON.xml", icon).toInt()
        val titleId = resources.addString("reseam_settings_title", "Reseam settings")?.toInt()
            ?: error("Could not add the Reseam settings title")

        appEntry {
            val row = newInstance(
                settingsItem.descriptor,
                settingsItemConstructor.proto,
                int(titleId), // Headline.
                nullObject, // Supporting text.
                callStatic(INTEGER, "valueOf", "(I)$INTEGER", int(iconId)), // Leading icon.
                nullObject, // Trailing icon.
                staticField(nestedSettingsDestination.enumConstant(REUSED_DESTINATION)),
                nullObject, // Logging tag.
            )
            call(SettingsEntry.rememberReseamRow, row)
        }

        settingsPageConstructor.before {
            val rows = call(SettingsEntry.withReseamRow, param(0).field(settingsMenuItems))
            whenNotNull(rows) {
                param(0).assign(
                    newInstance(
                        settingsMenuConstructor.owner, settingsMenuConstructor.proto, rows
                    )
                )
            }
        }
        log.info("Settings: ${settingsPageConstructor.descriptor} lists Reseam settings first.")

        settingsNavigation.before {
            whenInstanceOf(param(0), nestedSettingsDestination.descriptor) {
                openSettings()
                returnNull()
            }
        }
        log.info("Settings: ${settingsNavigation.descriptor} opens Reseam settings from its row.")
    }
}

private object SettingsResources
