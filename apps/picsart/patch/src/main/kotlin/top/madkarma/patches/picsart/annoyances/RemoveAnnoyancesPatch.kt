// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.annoyances

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.picsart.PicsartPatchStrings

val removeAnnoyances =
    patch(PicsartPatchStrings.removeAnnoyancesName) {
        description(PicsartPatchStrings.removeAnnoyancesDescription)
        compatibleWith("com.picsart.studio")

        execute {
            showCampaign.alwaysReturn()
            log.info("Annoyances: ${showCampaign.descriptor} skips in-app campaign popups.")
        }
    }
