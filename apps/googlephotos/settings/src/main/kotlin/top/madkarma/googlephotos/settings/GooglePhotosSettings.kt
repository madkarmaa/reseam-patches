// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.googlephotos.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.text

object GooglePhotosSettings : SettingsGroup(GooglePhotosStrings) {
    val mapboxAccessToken by text(default = "", key = "googlephotos.mapbox_access_token")
}
