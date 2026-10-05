// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.ultra

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.picsart.Ultra
import top.madkarma.patches.universal.signature.bypassSignatureChecks

val unlockUltra = patch("Unlock Ultra") {
    description("Unlocks Ultra-only features. Server credit balances remain unchanged.")
    compatibleWith("com.picsart.studio")
    dependsOn(bypassSignatureChecks)

    execute {
        if (ultraTier.type != tierType.type) error("Ultra tier does not match the plan's tier type")

        // Normalize every new snapshot, including defaults and refreshed account state.
        subscriptionConstructor.before {
            param(0).assign(call(Ultra.isUserSubscribed))
        }

        // Custom tool gates compare this level against a configured minimum.
        planConstructor.before {
            param(0).assign(call(Ultra.accessLevel))
        }

        planConstructor.after {
            thisObject.set(tierType, call(Ultra.tier))
            thisObject.set(
                permissions, call(Ultra.addPremiumPermissions, thisObject.field(permissions))
            )
        }

        log.info("Ultra: normalized subscription snapshots and plan permissions.")
    }
}
