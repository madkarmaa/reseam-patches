// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.launch

import app.reseam.patch.patch
import app.reseam.patch.settings.gate
import app.reseam.patch.settings.section
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.settings.truecallerSettings
import top.madkarma.patches.truecaller.shared.disableFeatureFlag
import top.madkarma.truecaller.settings.TruecallerSettings
import top.madkarma.truecaller.settings.TruecallerStrings

val disableAutoLaunch =
    patch(TruecallerPatchStrings.disableAutoLaunchName) {
        description(TruecallerPatchStrings.disableAutoLaunchDescription)
        compatibleWith("com.truecaller")

        settings(
            truecallerSettings,
            section(TruecallerStrings.sectionBehavior, TruecallerSettings.disableAutoLaunch),
        )

        execute {
            gate(TruecallerSettings.disableAutoLaunch) {
                afterCallScreenEnabled.alwaysReturn(false)
            }
            log.info(
                "Auto-launch: ${afterCallScreenEnabled.descriptor} disables automatic after-call screens.",
            )
            disableFeatureFlag("featureAutoAppLaunchPassive")
        }
    }
