// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.pro

import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("bitpit.launcher")

    execute {
        // Only the first flag grants Pro; retain the other entitlement flags.
        proCheckerConstructor.before {
            param(0).assign(bool(true))
        }

        val thankYou = resources.getString("purchase_pro_thank_you")
            ?: error("Unlock Pro: purchase_pro_thank_you string missing")

        val tagged = resources.setString(
            "purchase_pro_thank_you",
            "$thankYou\n\n$PATCH_ATTRIBUTION",
        )
        if (!tagged)
            error("Unlock Pro: purchase_pro_thank_you string not writable")
        log.info("Pro: thank-you label tagged.")

        log.info("Pro: unlocked via ${proCheckerConstructor.descriptor}.")
    }
}
