// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vi.device

import app.reseam.patch.className
import app.reseam.patch.descriptor
import app.reseam.patch.patch
import top.madkarma.patches.vi.ViPatchStrings

val disableDeviceChecks =
    patch(ViPatchStrings.disableDeviceChecksName) {
        description(ViPatchStrings.disableDeviceChecksDescription)
        compatibleWith("com.mventus.selfcare.activity")

        execute {
            val wrapper = manifest.applicationClass ?: error("Vi has no Application class")
            if (protectionStartup.owner != descriptor(wrapper))
                error("Vi's Application does not match the known protection wrapper")

            val application = className(originalApplication.descriptor)
            manifest.setAttributeString("application", "name", application)
            log.info(
                "Device checks: ${protectionStartup.descriptor} is bypassed by starting $application directly.",
            )
        }
    }
