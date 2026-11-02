// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.truecaller.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.toggle

object TruecallerSettings : SettingsGroup(TruecallerStrings) {
    val disableAutoLaunch by toggle(default = true, key = "truecaller.disable_auto_launch")
}
