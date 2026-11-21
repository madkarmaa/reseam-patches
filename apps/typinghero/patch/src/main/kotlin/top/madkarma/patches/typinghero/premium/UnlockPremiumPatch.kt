// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.typinghero.premium

import app.reseam.patch.ExternalPatch
import app.reseam.patch.patch
import app.reseam.patch.proto
import app.reseam.patch.replace
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.hasPermission
import top.madkarma.patches.shared.removePermission
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.typinghero.TypingHeroPatchStrings
import top.madkarma.typinghero.extensions.bindings.CustomerInfo

private const val INTERNET = "android.permission.INTERNET"
private const val NETWORK_STATE = "android.permission.ACCESS_NETWORK_STATE"

val unlockPremium =
    patch(TypingHeroPatchStrings.unlockPremiumName) {
        description(TypingHeroPatchStrings.unlockPremiumDescription)
        compatibleWith("sen.typinghero")
        // Without network access, Pairip's license check could never pass.
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        execute {
            // Every Premium check derives its state from the loaded customer info, so a verified
            // lifetime purchase unlocks the app without reaching RevenueCat.
            fetchCustomerInfo.replace {
                val customerInfo =
                    call(
                        buildCustomerInfo,
                        CustomerInfo.lifetimeCustomerInfo(string(PREMIUM_ENTITLEMENT)),
                        nullObject,
                        staticField(verified),
                        staticField(mainDataSource),
                        bool(false),
                        callStatic("java.util.Collections", "emptySet", proto("java.util.Set")),
                    )
                returnValue(customerInfo)
            }
            log.info(
                "Premium: ${fetchCustomerInfo.descriptor} returns a verified lifetime " +
                    "$PREMIUM_ENTITLEMENT purchase, accepted by ${premiumEntitlements.descriptor}.",
            )

            // The Premium status screen describes the lifetime purchase's access with this text.
            if (!resources.setStringEverywhere("active_for_lifetime", PATCH_ATTRIBUTION))
                error("Premium: active_for_lifetime string missing")
            log.info("Premium: active_for_lifetime shows the shared patch attribution.")

            // Premium needs no server, so the app runs offline only.
            manifest.edit {
                if (removePermission(INTERNET) == 0)
                    error("Premium: the manifest doesn't request $INTERNET")
                if (!hasPermission(NETWORK_STATE))
                    error("Premium: the manifest doesn't request $NETWORK_STATE")
            }
            log.info("Premium: the manifest no longer requests $INTERNET.")

            // The app configures RevenueCat at startup, and its internet permission check would
            // throw. Android grants the network state permission to every app that requests it, so
            // checking that instead passes; RevenueCat's requests then fail like any offline
            // request.
            if (configurePurchases.method.replaceAllStrings(INTERNET, NETWORK_STATE) != 1)
                error(
                    "Premium: expected one internet permission check in RevenueCat's configuration",
                )
            log.info(
                "Premium: ${configurePurchases.descriptor} configures RevenueCat without $INTERNET.",
            )
        }
    }
