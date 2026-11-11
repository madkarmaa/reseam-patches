// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.settings

import app.reseam.patch.*
import app.reseam.patch.settings.settingsHost
import top.madkarma.niagara.settings.bindings.SettingsEntry

private const val ROW_KEY = "reseam_settings"
private const val ROW_TITLE = "Reseam settings"
private const val ROW_SUMMARY = "Options for patched features"
private const val ROW_ICON = "ic_ip_act_sliders"

val niagaraSettings =
    settingsHost("niagara") {
        compatibleWith("bitpit.launcher")

        install {
            val icon =
                resources.id("drawable", ROW_ICON)
                    ?: error("Settings: Niagara drawable $ROW_ICON is missing")

            // Niagara's main settings list the Reseam row before every section.
            productivitySettings.before {
                val row =
                    newInstance(settingsRow.descriptor, "(Ljava/lang/String;)V", string(ROW_KEY))
                row.set(rowTitle, string(ROW_TITLE).cast(Type.CharSequence))
                row.set(rowSummary, string(ROW_SUMMARY).cast(Type.CharSequence))
                row.set(rowIcon, int(icon.toInt()))
                SettingsEntry.rememberRow(row)
                paramOfType(addRow.owner).call(addRow, row)
            }
            log.info("Settings: ${productivitySettings.descriptor} lists Reseam settings first.")

            rowClicked.before {
                whenTrue(SettingsEntry.openIfEntry(thisObject)) { returnVoid() }
            }
            log.info("Settings: ${rowClicked.descriptor} opens Reseam settings from its row.")
        }
    }
