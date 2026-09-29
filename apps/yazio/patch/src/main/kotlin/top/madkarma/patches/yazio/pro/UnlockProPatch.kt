@file:Suppress("unused")

package top.madkarma.patches.yazio.pro

import app.reseam.patch.*

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features and credits the patch on the Account screen.")
    compatibleWith("com.yazio.android")

    execute {
        val label = resources.getString(SUBSCRIPTION_LABEL_KEY)
        check(
            resources.setString(
                SUBSCRIPTION_LABEL_KEY, "Patched with ❤ by MadKarma ;)"
            )
        ) { "Unlock Pro: failed to rewrite string $SUBSCRIPTION_LABEL_KEY" }
        log.info("Pro: account subscription label rewritten.")

        val gates = subscriptionGates.all
        check(gates.size == 2) { "Unlock Pro: expected 2 subscription gates, found ${gates.size}" }
        gates.forEach { it.alwaysReturn(true) }
        log.info("Pro: forced ${gates.size} subscription gate(s) true (${gates.joinToString { it.descriptor }}).")

        val premiumParams = userModelCtor.method.parameterTypes
        check(premiumParams.count { it == descriptor(PREMIUM_TYPE) } == 1) { "Unlock Pro: user model premium param not unique in ${userModelCtor.descriptor}" }
        userModelCtor.before {
            val premium = paramOfType(PREMIUM_TYPE)
            whenNull(premium) {
                // NB: valueOf by backend name, NOT sget: R8 obfuscates the
                // enum fields (a/b/c) but the runtime names ("Subscription")
                // stay stable.
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

        storePremiumStatus.replace {
            // NB: same as above - sget would need the obfuscated field id;
            // valueOf("Pro") resolves the stable runtime name instead.
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
