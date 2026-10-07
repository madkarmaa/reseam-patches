// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.yazio.pro

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

private val subscriptionHelpers =
    klass("subscriptionHelpers") {
        strings("in_trial_period")
    }

// The two gates have no strings; the neighboring status-name mapper identifies their class.
internal val subscriptionGates =
    methods("subscriptionGates") {
        inClass(subscriptionHelpers)
        params("yazio.subscription.api.SubscriptionStatus")
        returns(Type.Boolean)
    }

private val userModel =
    klass("userModel") {
        strings("User(sex=")
    }

internal val userModelConstructor = userModel.method("<init>")

internal val storePremiumStatus =
    method("storePremiumStatus") {
        strings("getStorePremiumStatus")
    }
