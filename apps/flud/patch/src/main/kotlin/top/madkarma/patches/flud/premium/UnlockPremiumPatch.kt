// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.flud.premium

import app.reseam.patch.*

val unlockPremium = patch("Unlock Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.delphicoder.flud")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        isPremium.alwaysReturn(true)
        log.info("Premium: ${isPremium.descriptor} returns an unlocked entitlement.")

        publishPremium.before { paramOfType(Type.Boolean).assign(bool(true)) }
        log.info("Premium: ${publishPremium.descriptor} publishes unlocked state to all observers.")
    }
}
