package top.madkarma.patches.droplert

import app.reseam.patch.*
import app.reseam.patch.dex.*
import top.madkarma.patches.universal.removePairip

object Prefs : ExtClass("top.madkarma.droplert.extensions.Prefs") {
    val putBoolean = static("putBoolean", Type.Context, Type.String, Type.Boolean)
}

val CustomerInfo_getEntitlements =
    klass("com.revenuecat.purchases.CustomerInfo").method("getEntitlements")
val EntitlementInfos_get = klass("com.revenuecat.purchases.EntitlementInfos").method("get")
val EntitlementInfo_isActive = klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val customerInfoIsPremiumActive = method("customer premium active") {
    returns(Type.Boolean)
    params("com.revenuecat.purchases.CustomerInfo")
    strings("premium")
    calls(CustomerInfo_getEntitlements)
    calls(EntitlementInfos_get)
    calls(EntitlementInfo_isActive)
}

val isPremium = method("premium gate") {
    returns(Type.Boolean)
    paramCount(0)
    opcode(
        Opcode.CMP_LONG, Opcode.IGET_BOOLEAN, Opcode.INSTANCE_OF, Opcode.SGET_OBJECT
    )
}

val revenueCatStateUpdater = method("RevenueCat premium state updater") {
    paramCount(2)
    param(0, "com.revenuecat.purchases.CustomerInfo")
    returns(Type.Object)
    strings("premium")
    calls(CustomerInfo_getEntitlements)
    calls(EntitlementInfos_get)
    calls(EntitlementInfo_isActive)
}

val playPurchaseCallback = method("Play purchase result callback") {
    paramCount(1)
    returns(Type.Object)
    strings(
        "lifetime_premium",
        "Play unreachable — cannot disprove a lifetime purchase, leaving status untouched",
    )
}

val unconfiguredFallback = method("RevenueCat unconfigured fallback") {
    paramCount(3)
    returns(Type.Object)
    strings(
        "Purchases not configured yet, using cached or FREE tier",
        "RevenueCat network call failed, using cached status",
    )
}

val cachedStatusLoader = method("cached premium status loader") {
    paramCount(2)
    returns(Type.Object)
    strings("Failed to load cached premium status")
}

// Lifetime branch of the settings premium card
val premiumCardStatus = method("premium card status text") {
    strings("All features unlocked", "Renews: ")
}

private fun premiumFieldOf(updater: MethodTarget): FieldRef? {
    val target = updater.method
    return target.instructions.firstNotNullOfOrNull { insn ->
        insn.fieldRef?.takeIf { it.name == "PREMIUM" }
    }
}

private fun swapFreeToPremium(
    updater: MethodTarget,
    premiumField: FieldRef,
): Boolean {
    val target = updater.method
    val insns = target.instructions

    var promoted = 0
    for (i in insns.indices) {
        val insn = insns[i]
        if (insn.opcode != Opcode.SGET_OBJECT) continue

        val field = insn.fieldRef ?: continue
        if (field.name != "FREE" || field.definingClass != premiumField.definingClass) continue

        val dest = insn.regA ?: continue

        val replacement = Instruction.RegField(
            RegFieldInsn(
                Opcode.SGET_OBJECT.value.toUShort(), dest.toUShort(), 0u, premiumField
            ),
        )

        if (replacement.codeUnitSize != insns[i].codeUnitSize) return false
        target.replaceInstruction(i, replacement)

        promoted++
    }

    return promoted > 0
}

val unlockPremium = patch("Unlock Lifetime Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.shahzaman.pricetracker"("2.2.1"))
    dependsOn(removePairip)

    val skipOnboarding = boolOption(
        "skipOnboarding",
        title = "Skip onboarding",
        description = "Skips the setup and buy-premium screens, landing directly on home.",
        default = true,
    )

    execute {
        isPremium.method.alwaysReturn(true)
        customerInfoIsPremiumActive.method.alwaysReturn(true)

        EntitlementInfo_isActive.method.alwaysReturn(true)

        val premiumField = premiumFieldOf(revenueCatStateUpdater)
            ?: error("Premium: RevenueCat state updater not found")

        if (!swapFreeToPremium(revenueCatStateUpdater, premiumField)) {
            error("Premium: state updater writes no FREE state")
        }
        if (!swapFreeToPremium(playPurchaseCallback, premiumField)) {
            error("Premium: Play callback writes no FREE state")
        }
        if (!swapFreeToPremium(unconfiguredFallback, premiumField)) {
            error("Premium: unconfigured fallback writes no FREE state")
        }
        if (!swapFreeToPremium(cachedStatusLoader, premiumField)) {
            error("Premium: cached loader writes no FREE state")
        }

        if (premiumCardStatus.replaceAllStrings(
                "All features unlocked",
                "Patched with ❤ by MadKarma ;)",
            ) == 0
        ) {
            error("Premium: settings card status text not found")
        }
        log.info("Premium: settings card tagged.")

        if (options[skipOnboarding]) {
            appEntry.before {
                call(
                    Prefs.putBoolean, thisObject, string("has_completed_onboarding"), bool(true)
                )
                call(
                    Prefs.putBoolean, thisObject, string("has_seen_paywall"), bool(true)
                )
            }
        }
    }
}
