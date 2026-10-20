// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.annoyances

import app.reseam.patch.method

// CleverTap routes every campaign format through this method before displaying it.
internal val showCampaign = method("showCampaign") {
    strings("InAppController:displayNotification")
}
