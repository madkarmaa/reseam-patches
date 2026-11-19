// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.alwaysonwidgets.premium

import app.reseam.patch.ExternalPatch
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.alwaysonwidgets.AlwaysOnWidgetsPatchStrings

val unlockPremium =
    patch(AlwaysOnWidgetsPatchStrings.unlockPremiumName) {
        description(AlwaysOnWidgetsPatchStrings.unlockPremiumDescription)
        compatibleWith("xyz.inspoapps.alwaysonwidgets")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            publishEntitlement.before {
                thisObject.set(cachedEntitlement, staticField(premiumEntitlement))
            }
            log.info(
                "Premium: ${publishEntitlement.descriptor} always publishes the Premium entitlement.",
            )

            // Play Billing revokes Premium when it finds no purchase; keep every update entitled.
            updateEntitlement.before { param(0).assign(bool(true)) }
            log.info("Premium: ${updateEntitlement.descriptor} always stores Premium as purchased.")
        }
    }
