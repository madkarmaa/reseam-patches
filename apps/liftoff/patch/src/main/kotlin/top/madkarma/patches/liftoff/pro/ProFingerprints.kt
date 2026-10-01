// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.liftoff.pro

import app.reseam.patch.ExtClass
import app.reseam.patch.klass
import app.reseam.patch.method

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
// not enough - nothing exists to call it on. The mapper result therefore
// gets fabricated lifetime-pro entries (built by the `pro` extension, which
// owns all map assembly in plain Java) merged into both `active` and `all`:
// the subscription screen reads `active`, but other gates (e.g. the profile
// paywall) read `all`, and an empty `all` keeps them firing.

val revenueCatEntitlementIsActive =
    klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val revenueCatEntitlementsMapper = klass(
    "com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfosMapperKt"
).method("map")

val revenueCatActiveSubscriptions =
    klass("com.revenuecat.purchases.CustomerInfo").method("getActiveSubscriptions")

val superwallEntitlementIsActive =
    klass("com.superwall.sdk.models.entitlements.Entitlement").method("isActive")

val superwallSubscriptionStatusIsActive =
    klass("com.superwall.sdk.models.entitlements.SubscriptionStatus").method("isActive")

val superwallActiveEntitlements =
    klass("com.superwall.sdk.store.Entitlements").method("getActive")

object ProEntitlements : ExtClass("top.madkarma.liftoff.extensions.ProEntitlements") {
    val proActiveEntriesMap = static("proActiveEntriesMap", "java.util.Map")
}
