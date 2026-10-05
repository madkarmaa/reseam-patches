// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.liftoff.pro

import app.reseam.patch.*
import top.madkarma.patches.liftoff.ProEntitlements

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features, including shop items.")
    compatibleWith("com.gymbros.app")

    execute {
        revenueCatEntitlementIsActive.alwaysReturn(true)

        // Empty accounts need entries in both maps consumed by the React Native UI.
        revenueCatEntitlementsMapper.after {
            call(ProEntitlements.applyProEntitlements, capture("result"))
        }

        revenueCatActiveSubscriptions.replace {
            returnValue(singletonSet(string("pro")))
        }

        superwallEntitlementIsActive.alwaysReturn(true)
        superwallSubscriptionStatusIsActive.alwaysReturn(true)

        // Superwall also checks the active set; this constructor creates an active service entitlement.
        superwallActiveEntitlements.replace {
            val entitlement = newInstance(
                superwallEntitlementIsActive.owner,
                proto(Type.Void, Type.String),
                string("pro"),
            )
            returnValue(singletonSet(entitlement))
        }
    }
}

private fun CodeScope.singletonSet(value: ValueRef): ValueRef =
    callStatic("java.util.Collections", "singleton", proto("java.util.Set", Type.Object), value)
