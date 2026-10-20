// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.liftoff.pro

import app.reseam.patch.*
import top.madkarma.liftoff.extensions.bindings.ProEntitlements

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features, including shop items.")
    compatibleWith("com.gymbros.app")

    execute {
        revenueCatEntitlementIsActive.alwaysReturn(true)
        log.info("Pro: ${revenueCatEntitlementIsActive.descriptor} reports an active entitlement.")

        // Empty accounts need entries in both maps consumed by the React Native UI.
        revenueCatEntitlementsMapper.after {
            call(ProEntitlements.applyProEntitlements, result)
        }
        log.info("Pro: ${revenueCatEntitlementsMapper.descriptor} adds Pro entitlements to the UI account data.")

        revenueCatActiveSubscriptions.replace {
            returnValue(singletonSet(string("pro")))
        }
        log.info("Pro: ${revenueCatActiveSubscriptions.descriptor} returns the Pro subscription.")

        superwallEntitlementIsActive.alwaysReturn(true)
        log.info("Pro: ${superwallEntitlementIsActive.descriptor} reports an active entitlement.")

        superwallSubscriptionStatusIsActive.alwaysReturn(true)
        log.info("Pro: ${superwallSubscriptionStatusIsActive.descriptor} reports an active subscription.")

        // Superwall also checks the active set; this constructor creates an active service entitlement.
        superwallActiveEntitlements.replace {
            val entitlement = newInstance(
                superwallEntitlementIsActive.owner,
                proto(Type.Void, Type.String),
                string("pro"),
            )
            returnValue(singletonSet(entitlement))
        }
        log.info("Pro: ${superwallActiveEntitlements.descriptor} returns the Pro entitlement.")
    }
}

private fun CodeScope.singletonSet(value: ValueRef): ValueRef =
    callStatic("java.util.Collections", "singleton", proto("java.util.Set", Type.Object), value)
