// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.inshot.ai

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.inshot.InShotPatchStrings
import top.madkarma.patches.universal.signature.signatureKiller

// Menu entries of cloud AI tools that show regardless of their remote-config switch. Remove keeps
// its on-device Basic mode, and Portrait keeps the on-device AI Retouch and Face tools.
private val cloudToolButtons =
    mapOf(
        "ll_video_toolbar" to listOf("btn_video_enhance", "btn_enhance_voice"),
        "trackline_tool_bar_layout" to listOf("btn_enhance_voice"),
        "ll_image_toolbar" to listOf("btn_enhance", "btn_expand"),
        "fragment_image_remove" to listOf("advanced_layout"),
        "fragment_image_portrait" to listOf("ailight_layout"),
    )

val removeAiIntegrations =
    patch(InShotPatchStrings.removeAiIntegrationsName) {
        description(InShotPatchStrings.removeAiIntegrationsDescription)
        compatibleWith("com.camerasideas.instashot")
        // The native video engine kills the app when its signing certificate changes.
        dependsOn(signatureKiller)

        execute {
            cloudToolSwitches.forEach {
                it.alwaysReturn(false)
                log.info("AI: ${it.descriptor} reports the cloud tool as unavailable.")
            }

            for ((layout, buttons) in cloudToolButtons) {
                resources.editXml("layout", layout) {
                    for (button in buttons) {
                        val id =
                            resources.id("id", button) ?: error("AI: missing id resource $button")
                        val view =
                            findByAttribute("android:id", "@0x%08x".format(id.toInt()))
                                .singleOrNull() ?: error("AI: $layout has no single $button")

                        view["android:visibility"] = "gone"
                    }
                }
                log.info("AI: layout $layout hides ${buttons.joinToString()}.")
            }
        }
    }
