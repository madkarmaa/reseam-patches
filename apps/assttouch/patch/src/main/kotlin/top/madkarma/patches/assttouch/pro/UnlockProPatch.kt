// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.assttouch.pro

import app.reseam.patch.ExternalPatch
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.assttouch.AssistiveTouchPatchStrings

val unlockPro =
    patch(AssistiveTouchPatchStrings.unlockProName) {
        description(AssistiveTouchPatchStrings.unlockProDescription)
        compatibleWith("com.utae.assttouch")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            publishEntitlement.before {
                thisObject.set(cachedEntitlement, staticField(premiumEntitlement))
            }
            log.info(
                "Pro: ${publishEntitlement.descriptor} always publishes the Premium entitlement.",
            )

            // Play Billing revokes Premium when it finds no purchase; keep every update entitled.
            updateEntitlement.before { param(0).assign(bool(true)) }
            log.info("Pro: ${updateEntitlement.descriptor} always stores Premium as purchased.")
        }
    }
