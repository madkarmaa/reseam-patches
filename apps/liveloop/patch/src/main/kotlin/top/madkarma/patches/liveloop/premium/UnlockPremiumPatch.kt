// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.liveloop.premium

import app.reseam.patch.*
import top.madkarma.liveloop.preferences.bindings.Preferences
import top.madkarma.patches.liveloop.LiveLoopPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

private const val MANAGE_SUBSCRIPTION_TEXT = "Manage subscription on Google Play"

val unlockPremium =
    patch(LiveLoopPatchStrings.unlockPremiumName) {
        description(LiveLoopPatchStrings.unlockPremiumDescription)
        compatibleWith("com.wallpaper.liveloop")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            putPreferenceBoolean.before {
                param(1).assign(call(Preferences.premiumValue, param(0), param(1)))
            }
            log.info(
                "Premium: ${putPreferenceBoolean.descriptor} keeps pro_status enabled on every write.",
            )

            // Screens read pro_status straight from SharedPreferences, so seed it before any opens.
            appEntry { call(Preferences.unlockPremium, application) }
            log.info("Premium: app entry stores pro_status before the first screen opens.")

            resources.editXml("layout", "activity_settings") {
                findByAttribute("android:text", MANAGE_SUBSCRIPTION_TEXT)
                    .singleOrNull()
                    ?.set("android:text", PATCH_ATTRIBUTION)
                    ?: error("Premium: subscription row subtitle not found")
            }
            log.info("Premium: layout/activity_settings shows the patch attribution.")
        }
    }
