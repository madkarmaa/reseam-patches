@file:Suppress("unused")

package top.madkarma.patches.liftoff.pro

import app.reseam.patch.*

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features.")
    compatibleWith("com.gymbros.app")

    execute {
        revenueCatEntitlementIsActive.method.alwaysReturn(true)
        log.info("Pro: RevenueCat EntitlementInfo.isActive forced true (${revenueCatEntitlementIsActive.descriptor}).")

        revenueCatEntitlementsMapper.after {
            call(ProEntitlements.proActiveEntriesMap, capture("result"))
        }
        log.info("Pro: synthetic lifetime entitlement injected in ${revenueCatEntitlementsMapper.descriptor}.")

        // Pin the subscription-id set too, for length/subscription checks.
        revenueCatActiveSubscriptions.replace {
            returnValue(
                callStatic(
                    "java.util.Collections",
                    "singleton",
                    proto("java.util.Set", Type.Object),
                    string("pro"),
                )
            )
        }
        log.info("Pro: RevenueCat active subscriptions pinned in ${revenueCatActiveSubscriptions.descriptor}.")

        superwallEntitlementIsActive.method.alwaysReturn(true)
        log.info("Pro: Superwall Entitlement.isActive forced true (${superwallEntitlementIsActive.descriptor}).")

        // Base impl is `instance-of Active`; pinning true keeps every
        // subscription-status poll entitled.
        superwallSubscriptionStatusIsActive.method.alwaysReturn(true)
        log.info("Pro: Superwall SubscriptionStatus.isActive forced true (${superwallSubscriptionStatusIsActive.descriptor}).")

        // getAll() is empty for a never-purchased account, so aliasing
        // getActive to it stays empty and Superwall-side gates keep firing.
        // Fabricate a live Entitlement instead: Entitlement(String) defaults
        // to SERVICE_LEVEL with isActive=true. (The isActive() hooks above
        // cover direct method polls; most SDK decisions use
        // `instanceof Active` on the status object itself, which no method
        // hook can satisfy - a non-empty active set is the lever that works
        // from here.)
        superwallActiveEntitlements.replace {
            val entitlement = newInstance(
                "com.superwall.sdk.models.entitlements.Entitlement",
                proto(Type.Void, Type.String),
                string("pro"),
            )
            returnValue(
                callStatic(
                    "java.util.Collections",
                    "singleton",
                    proto("java.util.Set", Type.Object),
                    entitlement,
                )
            )
        }
        log.info("Pro: Superwall active entitlements pinned in ${superwallActiveEntitlements.descriptor}.")
    }
}
