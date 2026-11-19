// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.alwaysonwidgets.startup

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.extensions.alwaysonwidgets.bindings.StartupAnimation
import top.madkarma.patches.alwaysonwidgets.AlwaysOnWidgetsPatchStrings

val removeStartupAnimation =
    patch(AlwaysOnWidgetsPatchStrings.removeStartupAnimationName) {
        description(AlwaysOnWidgetsPatchStrings.removeStartupAnimationDescription)
        compatibleWith("xyz.inspoapps.alwaysonwidgets")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Take the restored-activity path: the splash draws without animating and the launch
            // gate opens as soon as the app is ready.
            mainActivityCreate.after {
                thisObject.field(introPlayed).call(setStateValue, StartupAnimation.introPlayed())
            }
            log.info(
                "Startup: ${mainActivityCreate.descriptor} marks ${introPlayed.name} as played, skipping the launch intro.",
            )
        }
    }
