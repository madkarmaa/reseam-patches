// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.yazio.pro

import app.reseam.patch.*

private val subscriptionHelpers =
    klass("subscriptionHelpers") {
        strings("in_trial_period")
    }

private val subscriptionStatus = klass("subscriptionStatus") {
    enumConstants("InTrialPeriod")
}

// The two gates have no strings; the neighboring status-name mapper identifies their class.
internal val subscriptionGates =
    methods("subscriptionGates") {
        inClass(subscriptionHelpers)
        params(subscriptionStatus.descriptor)
        returns(Type.Boolean)
    }

private val userModel =
    klass("userModel") {
        strings("User(sex=")
    }

internal val userModelConstructor = userModel.method("<init>")

// Several enums contain Subscription; only the user's premium type is a constructor input.
internal val premiumType = klass("premiumType") {
    enumConstants("Subscription")
    custom { descriptor in userModelConstructor.parameterTypes }
}

internal val subscriptionPremium = premiumType.enumConstant("Subscription")

private val storePremiumStates = klass("storePremiumStates") {
    enumConstants("Pro", "NotPro")
}

internal val proStorePremium = storePremiumStates.enumConstant("Pro")

internal val storePremiumStatus =
    method("storePremiumStatus") {
        strings("getStorePremiumStatus")
    }
