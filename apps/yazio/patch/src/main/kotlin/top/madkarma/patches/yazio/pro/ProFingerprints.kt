// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.yazio.pro

import app.reseam.patch.Type
import app.reseam.patch.method
import app.reseam.patch.methods

const val PREMIUM_TYPE = "yazio.user.api.PremiumType"
const val STORE_PREMIUM_STATUS = "yazio.payment.api.subscription.StorePremiumStatus"

// SubscriptionStatus readers: exactly two single-param Boolean helpers -
// full entitlement + paying subset. Force both true.
val subscriptionGates = methods("subscription pro gates") {
    returns(Type.Boolean)
    paramCount(1)
    param(0, "yazio.subscription.api.SubscriptionStatus")
}

// User model <init>: the constructor taking both PremiumType and Sex.
// Both producers (DTO mapper, backend validator) funnel through here, so
// defaulting null covers every read. (Sex excludes a PremiumType-only wrapper
// ctor that otherwise matches.)
val userModelCtor = method("user model constructor") {
    name("<init>")
    hasParam(PREMIUM_TYPE)
    hasParam("yazio.user.api.Sex")
}

// Play purchase lookup; pinned so store and backend agree instead of
// tripping the mismatch path.
val storePremiumStatus = method("store premium status") {
    strings("getStorePremiumStatus")
    returns("java.lang.Enum")
    paramCount(1)
}

// Account-screen row (Profile > gear > Account).
const val SUBSCRIPTION_LABEL_KEY = "user.settings.label.subscription"
