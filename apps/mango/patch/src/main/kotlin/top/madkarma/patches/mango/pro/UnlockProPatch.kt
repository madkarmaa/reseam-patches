// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mango.pro

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro =
    patch("Unlock Pro") {
        description("Unlocks Pro-only features.")
        compatibleWith("app.pinya.mango")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            defaultBillingMode.after {
                capture("billingMode").assign(staticField(lifetimeMode))
            }
            log.info(
                "Pro: ${initializeBilling.descriptor} initializes lifetime access and feature settings.",
            )

            if (
                !resources.setStringEverywhere(
                    "pro_thank_you",
                    PATCH_ATTRIBUTION,
                )
            )
                error("Pro: pro_thank_you string missing")
            log.info("Pro: pro_thank_you shows the shared patch attribution.")
        }
    }
