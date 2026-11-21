// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.saltplayer.pro

import app.reseam.patch.*
import top.madkarma.patches.saltplayer.SaltPlayerPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro =
    patch(SaltPlayerPatchStrings.unlockProName) {
        description(SaltPlayerPatchStrings.unlockProDescription)
        compatibleWith("com.salt.music")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // The combined Pro check every feature reads also goes through this getter.
            playStoreProGetter.alwaysReturn(true)
            log.info("Pro: ${playStoreProGetter.descriptor} always reports a Play Store purchase.")

            // Purchase checks that find no purchase would revoke Pro and clear its settings.
            if (proFlagSetters.all.isEmpty()) error("Pro: no Pro flag setters found")
            proFlagSetters.forEach {
                before { paramOfType(Type.Boolean).assign(bool(true)) }
                log.info("Pro: $descriptor always stores Pro as purchased.")
            }

            // The Pro screen introduces the Pro features spread across the settings screens.
            if (!resources.setStringEverywhere("additional_pro_features_intro", PATCH_ATTRIBUTION))
                error("Pro: additional_pro_features_intro string missing")
            log.info("Pro: additional_pro_features_intro shows the shared patch attribution.")
        }
    }
