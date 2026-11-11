// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.settings

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.resourceRef
import app.reseam.patch.settings.settingsHost
import top.madkarma.patches.truecaller.shared.featureFlagOverrides
import top.madkarma.truecaller.settings.bindings.SettingsEntry

val truecallerSettings =
    settingsHost("truecaller") {
        compatibleWith("com.truecaller")
        dependsOn(featureFlagOverrides)

        install {
            val rowId =
                resources.addId("reseam_settings_entry")
                    ?: error("Could not add the settings row ID")

            val titleId =
                resources.addString("reseam_settings_title", "Reseam settings")
                    ?: error("Could not add the settings title")

            val generalId =
                resources.id("id", "item_general")
                    ?: error("Truecaller General settings row is missing")

            resources.editXml("layout", "fragment_categories") {
                val general =
                    findByTag("TextView").single {
                        it["android:id"]?.let(::resourceRef) == generalId
                    }

                val row =
                    general.clone().apply {
                        setResourceRef("android:id", rowId)
                        setResourceRef("android:text", titleId)
                    }

                val group = general.parent ?: error("Truecaller settings group is missing")
                group.insertBefore(row, general)

                val divider =
                    createElement("include").apply { this["layout"] = "@layout/layout_divider" }
                group.insertBefore(divider, general)
            }

            settingsCategoriesCreated.after {
                SettingsEntry.bind(paramOfType(Type.View), int(rowId.toInt()))
            }
            log.info(
                "Settings: ${settingsCategoriesCreated.descriptor} opens Reseam settings when its row is tapped.",
            )
        }
    }
