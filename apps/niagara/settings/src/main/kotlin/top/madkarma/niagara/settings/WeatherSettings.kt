// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.niagara.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.TakesEffect
import app.reseam.patch.settings.Vector
import app.reseam.patch.settings.text

/** The key is read for every forecast request, so a new key applies immediately. */
object WeatherSettings : SettingsGroup(NiagaraStrings) {
    val weatherApiKey by
        text(
            default = "",
            key = "niagara.weather.api_key",
            icon = Vector.lucide("key-round"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )
}
