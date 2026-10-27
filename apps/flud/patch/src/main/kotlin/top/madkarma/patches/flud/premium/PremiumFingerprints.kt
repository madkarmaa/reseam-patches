// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.flud.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val premiumEntitlement = klass("premiumEntitlement") {
    strings("isPremium_")
}

internal val isPremium = method("isPremium") {
    inClass(premiumEntitlement)
    returns(Type.Boolean)
}

private val seedPremiumFromCache = method("seedPremiumFromCache") {
    strings("Failed to seed premium entitlement from cache")
}

// Startup publishes the cached entitlement through the same setter used by billing and its flow.
internal val publishPremium = method("publishPremium") {
    calledBy(seedPremiumFromCache)
    params(Type.Boolean)
}
