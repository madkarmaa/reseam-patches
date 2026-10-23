// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.xplayer.premium

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.extensions.xplayer.bindings.XPlayerPremium
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

private const val ALREADY_PREMIUM_TOAST_KEY = "toast_already_vip"

val unlockPremium = patch("Unlock premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("video.player.videoplayer")

    execute {
        booleanPreference.before {
            whenTrue(call(XPlayerPremium.isPremiumPreference, paramOfType(Type.String))) {
                returnTrue()
            }
        }
        log.info("Premium: ${booleanPreference.descriptor} enables the Premium preference.")

        updateCachedPremium.before { paramOfType(Type.Boolean).assign(bool(true)) }
        log.info("Premium: ${updateCachedPremium.descriptor} keeps billing refreshes in the Premium state.")

        purchaseStateConstructor.after {
            call(updateCachedPremium, thisObject, bool(true))
        }
        log.info("Premium: ${purchaseStateConstructor.descriptor} initializes Premium before billing connects.")

        check(resources.setString(ALREADY_PREMIUM_TOAST_KEY, PATCH_ATTRIBUTION)) {
            "Unlock premium: failed to rewrite string $ALREADY_PREMIUM_TOAST_KEY"
        }
        log.info("Premium: $ALREADY_PREMIUM_TOAST_KEY shows the shared patch attribution.")
    }
}
