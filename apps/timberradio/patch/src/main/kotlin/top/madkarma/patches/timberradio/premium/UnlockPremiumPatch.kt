// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.timberradio.premium

import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.timberradio.TimberRadioPatchStrings
import top.madkarma.timberradio.premium.bindings.PremiumCard

val unlockPremium =
    patch(TimberRadioPatchStrings.unlockPremiumName) {
        description(TimberRadioPatchStrings.unlockPremiumDescription)
        compatibleWith("com.msoft.timberradio")

        execute {
            // Every Premium gate and ad helper reads this preference.
            isPremium.alwaysReturn(true)
            log.info("Premium: ${isPremium.descriptor} reports an active subscription.")

            // Otherwise each startup subscription check resets Premium-only settings.
            restoreFreeUserState.alwaysReturn()
            log.info(
                "Premium: ${restoreFreeUserState.descriptor} keeps Premium-only settings.",
            )

            // The app has no subscribed version of the Settings Premium card.
            mainSettingsViewCreated.after {
                val binding = thisObject.fieldOfType(mainSettingsBinding.descriptor)
                PremiumCard.showAttribution(
                    binding.field(premiumCard),
                    binding.field(subscribeButton),
                    string(PATCH_ATTRIBUTION),
                )
            }
            log.info(
                "Premium: ${mainSettingsViewCreated.descriptor} shows the Premium card as a status card with the shared patch attribution.",
            )
        }
    }
