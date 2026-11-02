// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.wear

import app.reseam.patch.patch
import app.reseam.patch.settings.gate
import app.reseam.patch.settings.section
import top.madkarma.patches.truecaller.settings.truecallerSettings
import top.madkarma.patches.truecaller.shared.disableFeatureFlag
import top.madkarma.truecaller.settings.TruecallerSettings
import top.madkarma.truecaller.settings.TruecallerStrings
import top.madkarma.truecaller.ui.bindings.Interface

val hideWearSettings = patch("Hide Wear OS settings") {
    description("Hides Wear OS settings and shortcuts.")
    compatibleWith("com.truecaller")

    settings(
        truecallerSettings,
        section(TruecallerStrings.sectionSettings, TruecallerSettings.hideWearSettings)
    )

    execute {
        disableFeatureFlag("featurePromptWearApp")

        gate(TruecallerSettings.hideWearSettings) {
            watchSearchEntries.after {
                Interface.clearCollection(
                    result.cast(watchSearchItems.owner).field(watchSearchItems)
                )
            }
        }

        log.info("Wear OS: ${watchSearchEntries.descriptor} hides Wear OS search results.")
    }
}
