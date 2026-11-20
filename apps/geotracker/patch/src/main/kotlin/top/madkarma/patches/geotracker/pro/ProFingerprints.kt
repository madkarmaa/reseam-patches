// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.geotracker.pro

import app.reseam.patch.Type
import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

private val subscriptionTier =
    klass("subscriptionTier") {
        enumConstants("Free", "Basic", "Pro")
    }

internal val proTier = subscriptionTier.enumConstant("Pro")

private val paidTierState =
    klass("paidTierState") {
        strings("Paid(tier=")
    }

internal val paidTierStateConstructor = paidTierState.method("<init>")

private val loadedSubscription =
    klass("loadedSubscription") {
        strings("Success(currentTierState=")
    }

internal val loadedSubscriptionConstructor = loadedSubscription.method("<init>")

// The subscription cache stores tiers under these names.
private val subscriptionCache =
    klass("subscriptionCache") {
        strings("free", "basic", "pro")
    }

private val cacheSubscription =
    method("cacheSubscription") {
        inClass(subscriptionCache)
        hasParam(loadedSubscription.descriptor)
    }

// The repository caches every subscription it loads from RevenueCat. Its boolean forces a reload;
// the cache's own coroutine continuation also calls the cache, but takes no boolean.
internal val fetchSubscription =
    method("fetchSubscription") {
        calls(cacheSubscription)
        hasParam(Type.Boolean)
    }
