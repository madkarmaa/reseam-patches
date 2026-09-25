package top.madkarma.patches.droplert.premium

import app.reseam.patch.*
import app.reseam.patch.dex.*
import app.reseam.patch.native.FieldRef
import app.reseam.patch.native.Instruction
import app.reseam.patch.native.RegFieldInsn

object Prefs : ExtClass("top.madkarma.droplert.extensions.Prefs") {
    val putBoolean = static("putBoolean", Type.Context, Type.String, Type.Boolean)
}

val premiumLegacyVersions = setOf("2.2.1", "2.4.0")
val premiumSuspendVersions = setOf("2.4.1", "2.5.0", "2.5.1")
val tamperVersions = setOf("2.5.1")
val supportedVersions = premiumLegacyVersions + premiumSuspendVersions + tamperVersions

val CustomerInfo_getEntitlements =
    klass("com.revenuecat.purchases.CustomerInfo").method("getEntitlements")
val EntitlementInfos_get = klass("com.revenuecat.purchases.EntitlementInfos").method("get")
val EntitlementInfo_isActive = klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val isPremium = method("premium gate") {
    returns(Type.Boolean)
    paramCount(0)
    opcode(
        Opcode.CMP_LONG, Opcode.IGET_BOOLEAN, Opcode.INSTANCE_OF
    )
}

val revenueCatStateUpdater = method("RevenueCat premium state updater") {
    paramCount(2)
    param(0, "com.revenuecat.purchases.CustomerInfo")
    returns(Type.Object)
    calls(CustomerInfo_getEntitlements)
    calls(EntitlementInfos_get)
    calls(EntitlementInfo_isActive)
}

val playPurchaseCallback = method("Play purchase result callback") {
    paramCount(1)
    returns(Type.Object)
    strings(
        "Play unreachable — cannot disprove a lifetime purchase, leaving status untouched",
    )
}

// Lifetime branch of the settings premium card
val premiumCardStatus = method("premium card status text") {
    strings("All features unlocked")
}

// Direct Boolean premium check, gone once 2.4.1 made the checks suspend.
val customerInfoIsPremiumActive = method("customer premium active") {
    returns(Type.Boolean)
    params("com.revenuecat.purchases.CustomerInfo")
    calls(CustomerInfo_getEntitlements)
    calls(EntitlementInfos_get)
    calls(EntitlementInfo_isActive)
}

val unconfiguredFallback = method("RevenueCat unconfigured fallback") {
    paramCount(3)
    returns(Type.Object)
    strings(
        "RevenueCat network call failed, using cached status",
    )
}

val cachedStatusLoader = method("cached premium status loader") {
    paramCount(2)
    returns(Type.Object)
    strings("Failed to load cached premium status")
}

// 2.4.1 turned the premium checks into suspend functions (continuation
// param, Object return), so the direct Boolean forms are gone there.

// 2.4.1 turned the fallback into a suspend function (flag + continuation).
// The string occurs in exactly one method per release, so it selects alone.
val unconfiguredFallbackSuspend = method("RevenueCat unconfigured fallback (suspend)") {
    strings(
        "RevenueCat network call failed, using cached status",
    )
}

// 2.4.1 turned the loader into a suspend function (continuation only).
// Same single-method string as above.
val cachedStatusLoaderSuspend = method("cached premium status loader (suspend)") {
    strings("Failed to load cached premium status")
}

// 2.5.1 gates every per-product premium check behind this signature/
// install-age check: certified build + older than ~3 days reads as
// tampered, collapsing premium limits to FREE even with premium state on.
// The string is unique app-wide, so nothing else is needed.
val tamperCheck = method("signature/install-age tamper check") {
    strings("layout_state")
}

private fun premiumFieldOf(updater: MethodTarget): FieldRef? {
    val target = updater.method
    return target.instructions.firstNotNullOfOrNull { insn ->
        insn.fieldRef?.takeIf { it.name == "PREMIUM" }
    }
}

fun swapFreeToPremium(
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

// Shared body of the premium patch: version-independent gates.
fun PatchRuntime.runUnlockPremium(): FieldRef {
    isPremium.method.alwaysReturn(true)

    EntitlementInfo_isActive.method.alwaysReturn(true)

    val premiumField = premiumFieldOf(revenueCatStateUpdater)
        ?: error("Premium: RevenueCat state updater not found")

    if (!swapFreeToPremium(revenueCatStateUpdater, premiumField)) {
        error("Premium: state updater writes no FREE state")
    }
    if (!swapFreeToPremium(playPurchaseCallback, premiumField)) {
        error("Premium: Play callback writes no FREE state")
    }

    if (premiumCardStatus.replaceAllStrings(
            "All features unlocked",
            "Patched with ❤ by MadKarma ;)",
        ) == 0
    ) {
        error("Premium: settings card status text not found")
    }

    appEntry.before {
        call(
            Prefs.putBoolean, thisObject, string("has_seen_paywall"), bool(true)
        )
    }

    return premiumField
}
