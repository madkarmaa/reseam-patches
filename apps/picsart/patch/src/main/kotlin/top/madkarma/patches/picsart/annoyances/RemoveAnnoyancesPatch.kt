// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.annoyances

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val removeAnnoyances = patch("Remove annoyances") {
    description("Removes promotional popups from the app.")
    compatibleWith("com.picsart.studio")

    execute {
        showCampaign.alwaysReturn()
        log.info("Annoyances: ${showCampaign.descriptor} skips in-app campaign popups.")
    }
}
