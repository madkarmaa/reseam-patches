// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.larvelplayer.supporter

import app.reseam.patch.ExternalPatch
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.larvelplayer.supporter.bindings.Supporter
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockSupporter = patch("Unlock supporter") {
    description("Unlocks lifetime ad removal and supporter access to rewarded features.")
    compatibleWith("com.larvelplayer.app")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

    execute {
        loadAdRemoval.before { Supporter.unlock(thisObject) }
        log.info("Supporter: ${loadAdRemoval.descriptor} initializes supporter access before loading ad state.")

        if (supportIntroduction.method.replaceAllStrings(
                SUPPORT_DESCRIPTION, PATCH_ATTRIBUTION
            ) != 1
        ) error("Supporter: expected exactly one support introduction")
        log.info("Supporter: ${supportIntroduction.descriptor} displays the shared patch attribution.")
    }
}
