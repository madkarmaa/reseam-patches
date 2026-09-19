@file:Suppress("unused")

package top.madkarma.patches.liftoff

import app.reseam.patch.*

// Liftoff (com.gymbros.app) is an Expo app with no app-owned isPro flag in
// smali. Pro gating lives in the billing stack: RevenueCat entitlements
// (purchases 9.x via the RN bridge) orchestrated by Superwall paywalls
// (2.7.x) over Play BillingClient. Patching the SDK gates covers every
// placement at once instead of chasing minified JS call sites.
//
// The JS reads CustomerInfo through the hybrid mappers
// (`CustomerInfoMapperKt` -> `EntitlementInfosMapperKt.map`, which builds the
// `{all, active}` JSON object from the *map keys*). For an account that never
// purchased, the backend-backed maps are empty, so forcing `isActive()` is
// not enough — nothing exists to call it on. The mapper result therefore
// gets fabricated lifetime-pro entries (built by the `pro` extension, which
// owns all map assembly in plain Java) merged into `active`.

private val revenueCatEntitlementIsActive =
    klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

private val revenueCatEntitlementsMapper = klass(
    "com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfosMapperKt"
).method("map")

private val revenueCatActiveSubscriptions =
    klass("com.revenuecat.purchases.CustomerInfo").method("getActiveSubscriptions")

private val superwallEntitlementIsActive =
    klass("com.superwall.sdk.models.entitlements.Entitlement").method("isActive")

private val superwallSubscriptionStatusIsActive =
    klass("com.superwall.sdk.models.entitlements.SubscriptionStatus").method("isActive")

private val superwallActiveEntitlements =
    klass("com.superwall.sdk.store.Entitlements").method("getActive")

private val superwallAllEntitlements =
    klass("com.superwall.sdk.store.Entitlements").method("getAll")

object ProEntitlements : ExtClass("top.madkarma.liftoff.extensions.ProEntitlements") {
    val proActiveEntries = static("proActiveEntries", returns = "java.util.Map")
}

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features.")
    compatibleWith("com.gymbros.app"("2.16.0"))

    execute {
        revenueCatEntitlementIsActive.method.alwaysReturn(true)
        log.info("Pro: RevenueCat EntitlementInfo.isActive forced true.")

        revenueCatEntitlementsMapper.after {
            capture("result").callInterface(
                "java.util.Map", "get", proto(Type.Object, Type.Object),
                string("active"),
            ).cast("java.util.Map").callInterface(
                "java.util.Map", "putAll", proto(Type.Void, "java.util.Map"),
                call(ProEntitlements.proActiveEntries),
            )
        }
        log.info("Pro: synthetic lifetime entitlement injected into mapped active set.")

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
        log.info("Pro: RevenueCat CustomerInfo.getActiveSubscriptions pinned.")

        superwallEntitlementIsActive.method.alwaysReturn(true)
        log.info("Pro: Superwall Entitlement.isActive forced true.")

        // Base impl is `instance-of Active`; pinning true keeps every
        // subscription-status poll entitled.
        superwallSubscriptionStatusIsActive.method.alwaysReturn(true)
        log.info("Pro: Superwall SubscriptionStatus.isActive forced true.")

        // Same zero-arg Set shape: expose all known entitlements as active.
        superwallActiveEntitlements.replace {
            returnValue(thisObject.call(superwallAllEntitlements))
        }
        log.info("Pro: Superwall Entitlements.getActive aliased to getAll.")
    }
}
