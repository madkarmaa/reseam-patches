// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.textra.pro

import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.textra.TextraPatchStrings
import top.madkarma.patches.textra.shared.textraIntegrity
import top.madkarma.textra.pro.bindings.Pro

val unlockPro =
    patch(TextraPatchStrings.unlockProName) {
        description(TextraPatchStrings.unlockProDescription)
        compatibleWith("com.textra")
        dependsOn(textraIntegrity)

        execute {
            for (name in
                listOf("settings_have_upgraded_to_pro_summary", "upgraded_to_pro_summary")) {
                if (!resources.setStringEverywhere(name, PATCH_ATTRIBUTION))
                    error("Pro: missing attribution string $name")
                log.info("Pro: string/$name displays the shared patch attribution.")
            }

            licenseState.replace { returnValue(Pro.paidState()) }
            log.info(
                "Pro: ${licenseState.descriptor} returns the boxed paid state for billing, settings, and ad checks.",
            )
        }
    }
