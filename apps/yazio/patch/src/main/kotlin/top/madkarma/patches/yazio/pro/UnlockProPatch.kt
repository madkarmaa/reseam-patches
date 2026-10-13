// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.yazio.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

private const val SUBSCRIPTION_LABEL_KEY = "user.settings.label.subscription"

val unlockPro =
    patch("Unlock Pro") {
        description("Unlocks Pro-only features.")
        compatibleWith("com.yazio.android")

        execute {
            check(resources.setString(SUBSCRIPTION_LABEL_KEY, PATCH_ATTRIBUTION)) {
                "Unlock Pro: failed to rewrite string $SUBSCRIPTION_LABEL_KEY"
            }

            val gates = subscriptionGates.all
            check(gates.size == 2) { "Unlock Pro: expected 2 subscription gates, found ${gates.size}" }
            gates.forEach { it.alwaysReturn(true) }

            // paramOfType selects the first match, so require one premium parameter.
            check(userModelConstructor.parameterTypes.count { it == premiumType.descriptor } == 1) {
                "Unlock Pro: expected one user premium parameter"
            }
            userModelConstructor.before {
                val premium = paramOfType(premiumType.descriptor)
                whenNull(premium) {
                    premium.assign(staticField(subscriptionPremium))
                }
            }

            storePremiumStatus.replace {
                returnValue(staticField(proStorePremium))
            }
        }
    }
