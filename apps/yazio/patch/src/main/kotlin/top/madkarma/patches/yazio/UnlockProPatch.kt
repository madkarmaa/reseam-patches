@file:Suppress("unused")

package top.madkarma.patches.yazio

import app.reseam.patch.*

private const val PREMIUM_TYPE = "yazio.user.api.PremiumType"
private const val STORE_PREMIUM_STATUS = "yazio.payment.api.subscription.StorePremiumStatus"

// SubscriptionStatus readers: exactly two single-param Boolean helpers —
// full entitlement + paying subset. Force both true.
private val subscriptionGates = methods("subscription pro gates") {
    returns(Type.Boolean)
    paramCount(1)
    param(0, "yazio.subscription.api.SubscriptionStatus")
}

// User model <init>: the constructor taking both PremiumType and Sex.
// Both producers (DTO mapper, backend validator) funnel through here, so
// defaulting null covers every read. (Sex excludes a PremiumType-only wrapper
// ctor that otherwise matches.)
private val userModelCtor = method("user model constructor") {
    name("<init>")
    hasParam(PREMIUM_TYPE)
    hasParam("yazio.user.api.Sex")
}

// Play purchase lookup; pinned so store and backend agree instead of
// tripping the mismatch path.
private val storePremiumStatus = method("store premium status") {
    strings("getStorePremiumStatus")
    returns("java.lang.Enum")
    paramCount(1)
}

// Account-screen row (Profile > gear > Account).
private const val SUBSCRIPTION_LABEL_KEY = "user.settings.label.subscription"

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features and credits the patch on the Account screen.")
    compatibleWith("com.yazio.android")

    execute {
        val label = resources.getString(SUBSCRIPTION_LABEL_KEY)
        check(
            resources.setString(
                SUBSCRIPTION_LABEL_KEY, "Subscription patched with ❤ by MadKarma ;)"
            )
        ) { "Unlock Pro: failed to rewrite string $SUBSCRIPTION_LABEL_KEY" }
        log.info("Pro: account subscription label rewritten.")

        val gates = subscriptionGates.all
        check(gates.size == 2) { "Unlock Pro: expected 2 subscription gates, found ${gates.size}" }
        gates.forEach { it.alwaysReturn(true) }
        log.info("Pro: forced ${gates.size} subscription gate(s) true.")

        val premiumParams = userModelCtor.method.parameterTypes
        check(premiumParams.count { it == descriptor(PREMIUM_TYPE) } == 1) { "Unlock Pro: user model premium param not unique in ${userModelCtor.descriptor}" }
        userModelCtor.before { // FIXME simplify?
            val premium = paramOfType(PREMIUM_TYPE)
            whenNull(premium) {
                premium.assign(
                    callStatic(
                        PREMIUM_TYPE,
                        "valueOf",
                        proto(PREMIUM_TYPE, Type.String),
                        string("Subscription"),
                    ),
                )
            }
        }
        log.info("Pro: defaulting null premium to Subscription in ${userModelCtor.descriptor}.")

        storePremiumStatus.replace { // FIXME simplify?
            returnValue(
                callStatic(
                    STORE_PREMIUM_STATUS,
                    "valueOf",
                    proto(STORE_PREMIUM_STATUS, Type.String),
                    string("Pro"),
                ),
            )
        }
        log.info("Pro: store status pinned to Pro in ${storePremiumStatus.descriptor}.")
    }
}
