// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.larvelplayer.supporter

import app.reseam.patch.ExternalPatch
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.larvelplayer.supporter.bindings.Supporter

val unlockSupporter = patch("Unlock supporter") {
    description("Unlocks lifetime ad removal and supporter access to rewarded features.")
    compatibleWith("com.larvelplayer.app")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        loadAdRemoval.before { Supporter.unlock(thisObject) }
        log.info("Supporter: ${loadAdRemoval.descriptor} initializes supporter access before loading ad state.")
    }
}
