// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.mindfullaunch

import app.reseam.patch.*
import app.reseam.patch.settings.section
import top.madkarma.extensions.bindings.MindfulLaunch
import top.madkarma.niagara.settings.MindfulLaunchSettings
import top.madkarma.niagara.settings.NiagaraStrings
import top.madkarma.patches.niagara.NiagaraPatchStrings
import top.madkarma.patches.niagara.settings.niagaraSettings

val mindfulLaunch =
    patch(NiagaraPatchStrings.mindfulLaunchName) {
        description(NiagaraPatchStrings.mindfulLaunchDescription)
        compatibleWith("bitpit.launcher")

        settings(
            niagaraSettings,
            section(
                NiagaraStrings.sectionMindfulLaunch,
                MindfulLaunchSettings.mindfulLaunch,
                MindfulLaunchSettings.openLimit,
                MindfulLaunchSettings.openWindow,
                MindfulLaunchSettings.dailyLimit,
                MindfulLaunchSettings.countdown,
            ),
        )

        execute {
            feedsConstructor.after { MindfulLaunch.watchLeisureApps(thisObject.field(leisureApps)) }
            log.info("Mindful launch: ${feedsConstructor.descriptor} shares the leisure apps feed.")

            launchApp.before { MindfulLaunch.rememberTap(thisObject, paramOfType(Type.View)) }
            startPrimaryApp.redirectTo(MindfulLaunch.startActivity)
            log.info("Mindful launch: ${launchApp.descriptor} pauses overused leisure apps.")
        }
    }
