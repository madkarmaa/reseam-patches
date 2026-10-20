// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.ultra

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.universal.signature.signatureKiller
import top.madkarma.picsart.ultra.bindings.Ultra

val unlockUltra = patch("Unlock Ultra") {
    description("Unlocks Ultra-only features. Server credit balances remain unchanged.")
    compatibleWith("com.picsart.studio")
    dependsOn(signatureKiller)

    execute {
        // Normalize every new snapshot, including defaults and refreshed account state.
        subscriptionConstructor.before {
            param(0).assign(call(Ultra.isUserSubscribed))
        }
        log.info("Ultra: ${subscriptionConstructor.descriptor} reports an active subscription.")

        // Custom tool gates compare this level against a configured minimum.
        planConstructor.before {
            param(0).assign(call(Ultra.accessLevel))
        }

        planConstructor.after {
            thisObject.set(tierType, staticField(ultraTier))
            thisObject.set(
                permissions, call(Ultra.addPremiumPermissions, thisObject.field(permissions))
            )
        }

        log.info("Ultra: ${planConstructor.descriptor} sets the Ultra access level, tier, and permissions.")
    }
}
