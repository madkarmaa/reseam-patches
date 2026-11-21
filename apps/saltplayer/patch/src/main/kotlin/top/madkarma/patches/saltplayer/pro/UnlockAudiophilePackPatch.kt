// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.saltplayer.pro

import app.reseam.patch.*
import top.madkarma.patches.saltplayer.SaltPlayerPatchStrings

val unlockAudiophilePack =
    patch(SaltPlayerPatchStrings.unlockAudiophilePackName) {
        description(SaltPlayerPatchStrings.unlockAudiophilePackDescription)
        compatibleWith("com.salt.music")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            audiophilePackGetter.alwaysReturn(true)
            log.info(
                "Audiophile Pack: ${audiophilePackGetter.descriptor} always reports a purchase.",
            )
        }
    }
