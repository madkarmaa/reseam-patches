// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.niagara.settings

import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.TakesEffect
import app.reseam.patch.settings.Vector
import app.reseam.patch.settings.text
import app.reseam.patch.settings.toggle

/**
 * Every value is read when a leisure app is launched, so changes apply immediately. The limits are
 * whole numbers the user types; a value that is not one reads as the default.
 */
object MindfulLaunchSettings : SettingsGroup(NiagaraStrings) {
    val mindfulLaunch by
        toggle(
            default = true,
            key = "niagara.mindful_launch.enabled",
            icon = Vector.lucide("hourglass"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Open count; 0 turns the rule off.
    val openLimit by
        text(
            default = "5",
            key = "niagara.mindful_launch.open_limit",
            icon = Vector.lucide("repeat"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Minutes.
    val openWindow by
        text(
            default = "60",
            key = "niagara.mindful_launch.open_window",
            icon = Vector.lucide("calendar-clock"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Minutes; 0 turns the rule off.
    val dailyLimit by
        text(
            default = "60",
            key = "niagara.mindful_launch.daily_limit",
            icon = Vector.lucide("timer"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Seconds; 0 leaves the open button unlocked.
    val countdown by
        text(
            default = "10",
            key = "niagara.mindful_launch.countdown",
            icon = Vector.lucide("hand"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )
}
