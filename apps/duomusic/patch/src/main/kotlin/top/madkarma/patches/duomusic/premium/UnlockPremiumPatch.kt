// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.duomusic.premium

import app.reseam.patch.*
import top.madkarma.patches.duomusic.DuoMusicPatchStrings

val unlockPremium =
    patch(DuoMusicPatchStrings.unlockPremiumName) {
        description(DuoMusicPatchStrings.unlockPremiumDescription)
        compatibleWith("com.orhotechnologies.orhoduomusic")

        execute {
            isProUser.alwaysReturn(true)
            log.info("Premium: ${isProUser.descriptor} reports the lifetime purchase as owned.")

            // The startup restore broadcasts false when Play has no purchase, hiding the Pro badge.
            notifyProStatus.before { param(0).assign(bool(true)) }
            log.info(
                "Premium: ${notifyProStatus.descriptor} always notifies screens of Pro status.",
            )
        }
    }
