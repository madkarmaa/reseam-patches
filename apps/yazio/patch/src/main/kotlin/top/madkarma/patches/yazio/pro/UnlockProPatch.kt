// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.yazio.pro

import app.reseam.patch.*

private const val PREMIUM_TYPE = "yazio.user.api.PremiumType"
private const val STORE_PREMIUM_STATUS = "yazio.payment.api.subscription.StorePremiumStatus"
private const val SUBSCRIPTION_LABEL_KEY = "user.settings.label.subscription"

val unlockPro =
    patch("Unlock Pro") {
        description("Unlocks Pro-only features.")
        compatibleWith("com.yazio.android")

        execute {
            check(resources.setString(SUBSCRIPTION_LABEL_KEY, "Patched with ❤ by MadKarma ;)")) {
                "Unlock Pro: failed to rewrite string $SUBSCRIPTION_LABEL_KEY"
            }

            val gates = subscriptionGates.all
            check(gates.size == 2) { "Unlock Pro: expected 2 subscription gates, found ${gates.size}" }
            gates.forEach { it.alwaysReturn(true) }

            // paramOfType selects the first match, so require one premium parameter.
            check(userModelConstructor.parameterTypes.count { it == descriptor(PREMIUM_TYPE) } == 1) {
                "Unlock Pro: expected one user premium parameter"
            }
            userModelConstructor.before {
                val premium = paramOfType(PREMIUM_TYPE)
                whenNull(premium) {
                    premium.assign(enumByName(PREMIUM_TYPE, "Subscription"))
                }
            }

            storePremiumStatus.replace {
                returnValue(enumByName(STORE_PREMIUM_STATUS, "Pro"))
            }
        }
    }

// R8 renames the fields of both enums; valueOf retains their readable entry names.
private fun CodeScope.enumByName(type: String, name: String): ValueRef =
    callStatic(type, "valueOf", proto(type, Type.String), string(name))
