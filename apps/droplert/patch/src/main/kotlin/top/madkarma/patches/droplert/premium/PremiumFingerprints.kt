// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.droplert.premium

import app.reseam.patch.*

internal val entitlementIsActive =
    klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

// The updater and direct check share the entitlement call; their return types are disjoint.
internal val revenueCatStateUpdater = method("revenueCatStateUpdater") {
    calls(entitlementIsActive)
    hasParam("com.revenuecat.purchases.CustomerInfo")
    returns(Type.Object)
}

// This gate has no strings and is the manager's only zero-argument Boolean method.
internal val isPremium = method("isPremium") {
    inClass(klass(revenueCatStateUpdater.owner))
    params()
    returns(Type.Boolean)
}

internal val playPurchaseCallback = method("playPurchaseCallback") {
    strings("Play unreachable — cannot disprove a lifetime purchase, leaving status untouched")
}

internal val premiumCardStatus = method("premiumCardStatus") {
    strings("All features unlocked")
}

internal val customerInfoIsPremiumActive = method("customerInfoIsPremiumActive") {
    calls(entitlementIsActive)
    params("com.revenuecat.purchases.CustomerInfo")
    returns(Type.Boolean)
}

// Both direct and suspend releases retain these diagnostic strings.
internal val unconfiguredFallbacks = methods("unconfiguredFallbacks") {
    strings("RevenueCat network call failed, using cached status")
}

internal val cachedStatusLoaders = methods("cachedStatusLoaders") {
    strings("Failed to load cached premium status")
}

internal val tamperCheck = method("tamperCheck") {
    strings("layout_state")
}

private val premiumTier = klass("premiumTier") {
    strings("PREMIUM")
    extends("java.lang.Enum")
}

internal val premium = premiumTier.field("PREMIUM")
