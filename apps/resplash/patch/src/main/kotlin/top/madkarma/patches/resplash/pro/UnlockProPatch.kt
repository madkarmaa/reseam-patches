// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.resplash.pro

import app.reseam.patch.ExternalPatch
import app.reseam.patch.patch
import top.madkarma.patches.resplash.ResplashPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

// A constant row with the table's columns, so the observer always maps ResplashPro(entitled=true),
// even before any purchase row exists.
private const val ENTITLED_QUERY = "SELECT 1 AS entitled, 1 AS id"

val unlockPro =
    patch(ResplashPatchStrings.unlockProName) {
        description(ResplashPatchStrings.unlockProDescription)
        compatibleWith("com.b_lam.resplash")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            if (
                observeResplashPro.method.replaceAllStrings(RESPLASH_PRO_QUERY, ENTITLED_QUERY) != 1
            )
                error("Pro: expected exactly one resplash_pro query")
            log.info("Pro: ${observeResplashPro.descriptor} always reports an entitled Pro row.")

            // Pro users see the donate entry; support_development also titles About and Donation.
            val donateId =
                resources.id("id", "action_donate") ?: error("Pro: donate drawer entry missing")
            resources.editXml("menu", "menu_bottom_navigation_drawer") {
                val donateItem =
                    findByAttribute("android:id", "@0x${donateId.toString(16)}").singleOrNull()
                        ?: error("Pro: expected exactly one donate drawer entry")

                donateItem["android:title"] = PATCH_ATTRIBUTION
            }
            log.info("Pro: menu/menu_bottom_navigation_drawer shows the shared patch attribution.")
        }
    }
