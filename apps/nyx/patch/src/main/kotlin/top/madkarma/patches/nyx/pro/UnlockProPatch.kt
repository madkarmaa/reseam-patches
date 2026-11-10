// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.nyx.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.nyx.NyxPatchStrings

val unlockPro =
    patch(NyxPatchStrings.unlockProName) {
        description(NyxPatchStrings.unlockProDescription)
        compatibleWith("com.awedea.nyx")

        execute {
            hasActivePurchase.alwaysReturn(true)
            log.info(
                "Pro: ${hasActivePurchase.descriptor} reports an active purchase to feature checks and billing observers.",
            )
        }
    }
