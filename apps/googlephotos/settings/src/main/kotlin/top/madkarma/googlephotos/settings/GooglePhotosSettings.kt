// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.googlephotos.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.TakesEffect
import app.reseam.patch.settings.Vector
import app.reseam.patch.settings.text

/** The token is read whenever a map is shown, so a new token applies immediately. */
object GooglePhotosSettings : SettingsGroup(GooglePhotosStrings) {
    val mapboxAccessToken by
        text(
            default = "",
            key = "googlephotos.mapbox_access_token",
            icon = Vector.lucide("key-round"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )
}
