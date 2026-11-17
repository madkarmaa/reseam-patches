// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.truecaller.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.Vector
import app.reseam.patch.settings.toggle

object TruecallerSettings : SettingsGroup(TruecallerStrings) {
    val disableAutoLaunch by
        toggle(
            default = true,
            key = "truecaller.disable_auto_launch",
            icon = Vector.lucide("phone-off"),
        )
    val hideMessages by
        toggle(
            default = true,
            key = "truecaller.hide_messages",
            icon = Vector.lucide("message-square-off"),
        )
    val hideBlocking by
        toggle(
            default = true,
            key = "truecaller.hide_blocking",
            icon = Vector.lucide("shield-off"),
        )
    val hidePremium by
        toggle(default = true, key = "truecaller.hide_premium", icon = Vector.lucide("crown"))
    val hideFamily by
        toggle(default = true, key = "truecaller.hide_family", icon = Vector.lucide("users"))
    val hideScamFeed by
        toggle(
            default = true,
            key = "truecaller.hide_scam_feed",
            icon = Vector.lucide("shield-alert"),
        )
    val hideWearSettings by
        toggle(
            default = true,
            key = "truecaller.hide_wear_settings",
            icon = Vector.lucide("watch"),
        )
}
