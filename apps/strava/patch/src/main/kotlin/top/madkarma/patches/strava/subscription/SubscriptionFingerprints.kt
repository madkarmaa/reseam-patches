// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.strava.subscription

import app.reseam.patch.klass
import app.reseam.patch.method

// The subscription models keep their names through obfuscation.
private val subscriptionDetail = klass("com.strava.subscriptions.data.models.SubscriptionDetail")

internal val detailIsPremium = subscriptionDetail.method("isPremium")

// Only the cached subscription store copies the win-back flag into preferences.
private val storeSubscription =
    method("storeSubscription") { calls(subscriptionDetail.method("isWinback")) }

// The store implements SubscriptionAccessSnapshot, whose method names survive obfuscation.
internal val snapshotIsPremium =
    method("snapshotIsPremium") {
        inClass(klass(storeSubscription.owner))
        name("isPremium")
    }

// Modular screens such as the You tab's Progress page show this locked feature preview to free
// accounts. A null module is dropped, as when the server leaves out its frame data.
internal val createDataPeekUpsell =
    klass(
            "com.strava.subscriptionsdatapeek.modularui.modularcomponents.converters.DataPeekUpsellConverter",
        )
        .method("createModule")
