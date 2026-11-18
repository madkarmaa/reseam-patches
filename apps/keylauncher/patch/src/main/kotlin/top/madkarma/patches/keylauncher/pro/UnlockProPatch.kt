// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.keylauncher.pro

import app.reseam.patch.*
import top.madkarma.patches.keylauncher.KeyLauncherPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro =
    patch(KeyLauncherPatchStrings.unlockProName) {
        description(KeyLauncherPatchStrings.unlockProDescription)
        compatibleWith("com.loitran.minimalt9launcher.free")

        execute {
            isProPurchased.alwaysReturn(true)
            log.info("Pro: ${isProPurchased.descriptor} reports Pro as purchased.")

            storedProType.replace { returnValue(staticField(lifetimeProType)) }
            log.info("Pro: ${storedProType.descriptor} reports a lifetime Pro type.")

            if (!resources.setStringEverywhere("pro_upgrade_header_subtitle", PATCH_ATTRIBUTION))
                error("Pro: upgrade header subtitle is missing")
            log.info(
                "Pro: string/pro_upgrade_header_subtitle displays the shared patch attribution.",
            )
        }
    }
