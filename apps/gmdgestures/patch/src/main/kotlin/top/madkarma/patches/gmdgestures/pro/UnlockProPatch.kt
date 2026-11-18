// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.gmdgestures.pro

import app.reseam.patch.ExternalPatch
import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.gmdgestures.GmdGesturesPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro =
    patch(GmdGesturesPatchStrings.unlockProName) {
        description(GmdGesturesPatchStrings.unlockProDescription)
        compatibleWith("com.goodmooddroid.gestures")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            loadCachedEntitlement.alwaysReturn(true)
            log.info("Pro: ${loadCachedEntitlement.descriptor} reports a cached Pro purchase.")

            // Play Billing revokes Pro when it finds no purchase; keep every update unlocked.
            setProUnlocked.before { param(0).assign(bool(true)) }
            log.info("Pro: ${setProUnlocked.descriptor} always stores Pro as unlocked.")

            if (!resources.setStringEverywhere("pro_already_unlocked", PATCH_ATTRIBUTION))
                error("Pro: pro_already_unlocked string missing")
            log.info("Pro: pro_already_unlocked shows the shared patch attribution.")
        }
    }
