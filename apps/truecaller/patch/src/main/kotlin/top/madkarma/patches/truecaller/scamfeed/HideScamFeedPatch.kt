// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.scamfeed

import app.reseam.patch.patch
import app.reseam.patch.settings.gate
import app.reseam.patch.settings.section
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.settings.truecallerSettings
import top.madkarma.truecaller.settings.TruecallerSettings
import top.madkarma.truecaller.settings.TruecallerStrings

val hideScamFeed =
    patch(TruecallerPatchStrings.hideScamFeedName) {
        description(TruecallerPatchStrings.hideScamFeedDescription)
        compatibleWith("com.truecaller")

        settings(
            truecallerSettings,
            section(TruecallerStrings.sectionNavigation, TruecallerSettings.hideScamFeed),
        )

        execute {
            gate(TruecallerSettings.hideScamFeed) {
                scamFeedEnabled.alwaysReturn(false)
            }
            log.info(
                "Scam feed: ${scamFeedEnabled.descriptor} disables Scam Feed and its notifications.",
            )
        }
    }
