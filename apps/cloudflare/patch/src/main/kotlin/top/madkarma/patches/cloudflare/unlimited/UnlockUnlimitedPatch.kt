// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.cloudflare.unlimited

import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockUnlimited = patch("Unlock WARP+ Unlimited") {
    description(
        "Unlocks the local WARP+ Unlimited account state. Does not unlock server-side WARP+."
    )
    compatibleWith("com.cloudflare.onedotonedotonedotone")

    execute {
        // Apply to defaults, cached accounts, and API refreshes while preserving team migration.
        accountConstructor.before {
            val accountType = paramOfType(accountTypes.descriptor)
            whenNotEqual(accountType, staticField(teamAccount)) {
                accountType.assign(staticField(unlimitedAccount))
            }
        }
        log.info("WARP+: ${accountConstructor.descriptor} reports UNLIMITED for consumer accounts and preserves TEAM accounts.")

        accountSettingsConstructor.before {
            // The display model takes the license key immediately after the app state.
            param(1).assign(string(PATCH_ATTRIBUTION))
        }

        log.info("WARP+: ${accountSettingsConstructor.descriptor} displays the patch attribution as the license key.")
    }
}
