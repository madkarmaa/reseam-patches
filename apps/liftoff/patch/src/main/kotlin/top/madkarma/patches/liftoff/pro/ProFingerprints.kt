// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.liftoff.pro

import app.reseam.patch.klass
import app.reseam.patch.method

// Both billing SDKs retain their class and method names.
internal val revenueCatEntitlementIsActive =
    klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

internal val revenueCatEntitlementsMapper = klass(
    "com.revenuecat.purchases.hybridcommon.mappers.EntitlementInfosMapperKt"
).method("map")

internal val revenueCatActiveSubscriptions =
    klass("com.revenuecat.purchases.CustomerInfo").method("getActiveSubscriptions")

internal val superwallEntitlementIsActive =
    klass("com.superwall.sdk.models.entitlements.Entitlement").method("isActive")

internal val superwallSubscriptionStatusIsActive =
    klass("com.superwall.sdk.models.entitlements.SubscriptionStatus").method("isActive")

internal val superwallActiveEntitlements =
    klass("com.superwall.sdk.store.Entitlements").method("getActive")
