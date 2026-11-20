// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.fotmob.plus

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val subscriptionService =
    klass("subscriptionService") {
        strings("rc_promo_FotMob+")
    }

// Every ad, app icon, and membership check asks this method; it is the only one in the service
// that reads the cached subscription flag.
internal val hasValidSubscription =
    method("hasValidSubscription") {
        inClass(subscriptionService)
        params()
        returns(Type.Boolean)
        accesses { type("Ljava/lang/Boolean;") }
    }

private val toEntitlement =
    method("toEntitlement") {
        inClass(subscriptionService)
        hasParam("com.revenuecat.purchases.EntitlementInfo")
    }

// The membership screen and the trial reminder read the user's entitlements from the one service
// method that converts RevenueCat entitlements into the app's model.
internal val activeEntitlements =
    method("activeEntitlements") {
        inClass(subscriptionService)
        calls(toEntitlement)
    }

internal val entitlement =
    klass("entitlement") {
        strings("Entitlement(identifier=")
    }

internal val entitlementConstructor = entitlement.method("<init>")
