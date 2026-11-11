// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.niagara.settings

import app.reseam.patch.settings.Choice
import app.reseam.patch.settings.SettingsGroup
import app.reseam.patch.settings.TakesEffect
import app.reseam.patch.settings.Vector
import app.reseam.patch.settings.choice
import app.reseam.patch.settings.toggle

/** Every value is read when a leisure app is launched, so changes apply immediately. */
object MindfulLaunchSettings : SettingsGroup(NiagaraStrings) {
    val mindfulLaunch by
        toggle(
            default = true,
            key = "niagara.mindful_launch.enabled",
            icon = Vector.lucide("hourglass"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Values are open counts; "0" turns the rule off.
    val openLimit by
        choice(
            default = "5",
            choices =
                listOf(
                    Choice("0", NiagaraStrings.ruleOff),
                    Choice("3", NiagaraStrings.openLimit3),
                    Choice("5", NiagaraStrings.openLimit5),
                    Choice("10", NiagaraStrings.openLimit10),
                    Choice("15", NiagaraStrings.openLimit15),
                    Choice("20", NiagaraStrings.openLimit20),
                ),
            key = "niagara.mindful_launch.open_limit",
            icon = Vector.lucide("repeat"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Values are minutes.
    val openWindow by
        choice(
            default = "60",
            choices =
                listOf(
                    Choice("15", NiagaraStrings.duration15Minutes),
                    Choice("30", NiagaraStrings.duration30Minutes),
                    Choice("60", NiagaraStrings.duration1Hour),
                    Choice("120", NiagaraStrings.duration2Hours),
                    Choice("240", NiagaraStrings.duration4Hours),
                ),
            key = "niagara.mindful_launch.open_window",
            icon = Vector.lucide("calendar-clock"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Values are minutes; "0" turns the rule off.
    val dailyLimit by
        choice(
            default = "60",
            choices =
                listOf(
                    Choice("0", NiagaraStrings.ruleOff),
                    Choice("15", NiagaraStrings.duration15Minutes),
                    Choice("30", NiagaraStrings.duration30Minutes),
                    Choice("45", NiagaraStrings.duration45Minutes),
                    Choice("60", NiagaraStrings.duration1Hour),
                    Choice("90", NiagaraStrings.duration90Minutes),
                    Choice("120", NiagaraStrings.duration2Hours),
                    Choice("180", NiagaraStrings.duration3Hours),
                ),
            key = "niagara.mindful_launch.daily_limit",
            icon = Vector.lucide("timer"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )

    // Values are seconds.
    val countdown by
        choice(
            default = "10",
            choices =
                listOf(
                    Choice("3", NiagaraStrings.countdown3),
                    Choice("5", NiagaraStrings.countdown5),
                    Choice("10", NiagaraStrings.countdown10),
                    Choice("15", NiagaraStrings.countdown15),
                    Choice("20", NiagaraStrings.countdown20),
                    Choice("30", NiagaraStrings.countdown30),
                    Choice("60", NiagaraStrings.countdown60),
                ),
            key = "niagara.mindful_launch.countdown",
            icon = Vector.lucide("hand"),
            takesEffect = TakesEffect.IMMEDIATELY,
        )
}
