package top.madkarma.patches.droplert.premium

import app.reseam.patch.Branch0Insn
import app.reseam.patch.Instruction
import app.reseam.patch.MethodTarget
import app.reseam.patch.Type
import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.codeUnitSize
import app.reseam.patch.dex.fieldRef
import app.reseam.patch.dex.opcode
import app.reseam.patch.invoke
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

val CustomerInfo_getEntitlements = klass("com.revenuecat.purchases.CustomerInfo").method("getEntitlements")
val EntitlementInfos_get = klass("com.revenuecat.purchases.EntitlementInfos").method("get")
val EntitlementInfo_isActive = klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val customerInfoIsPremiumActive =
    method("customer premium active") {
        returns(Type.Boolean)
        params("com.revenuecat.purchases.CustomerInfo")
        strings("premium")
        calls(CustomerInfo_getEntitlements)
        calls(EntitlementInfos_get)
        calls(EntitlementInfo_isActive)
    }

// val System_currentTimeMillis = klass("java.lang.System").method("currentTimeMillis")
// val Long_longValue = klass("java.lang.Long").method("longValue")

val isPremium =
    method("premium gate") {
        returns(Type.Boolean)
        paramCount(0)
        // calls(System_currentTimeMillis)
        // calls(Long_longValue)
        opcode(Opcode.CMP_LONG, Opcode.IGET_BOOLEAN, Opcode.INSTANCE_OF, Opcode.SGET_OBJECT)
    }

val revenueCatStateUpdater =
    method("RevenueCat premium state updater") {
        paramCount(2)
        param(0, "com.revenuecat.purchases.CustomerInfo")
        returns(Type.Object)
        strings("premium")
        calls(CustomerInfo_getEntitlements)
        calls(EntitlementInfos_get)
        calls(EntitlementInfo_isActive)
    }

val playPurchaseCallback =
    method("Play purchase result callback") {
        paramCount(1)
        returns(Type.Object)
        strings(
            "lifetime_premium",
            "Play unreachable — cannot disprove a lifetime purchase, leaving status untouched",
        )
    }

/**
 * The Compose UI observes the premium state flow, which is computed from real
 * purchase data in two places: the RevenueCat customer-info updater and the
 * Play purchase callback. Both select a FREE state when no purchase exists, so
 * forcing the boolean gates is not enough. This replaces the branch that
 * selects the FREE state with a size-neutral jump to the PREMIUM assignment
 * that follows it. No code shifts, no try-table or branch-target changes.
 */
private fun forcePremiumState(updater: MethodTarget): Boolean {
    val target = updater.method
    val insns = target.instructions
    val units = IntArray(insns.size)
    var offset = 0
    for (i in insns.indices) {
        units[i] = offset
        offset += insns[i].codeUnitSize
    }

    fun indexOfUnit(unit: Int): Int {
        for (i in units.indices) if (units[i] == unit) return i
        return -1
    }
    for (i in insns.indices) {
        val insn = insns[i]
        val branchOffset =
            when (insn.opcode) {
                Opcode.IF_EQZ -> (insn as? Instruction.Branch)?.value0?.offset
                Opcode.IF_NE -> (insn as? Instruction.Branch2)?.value0?.offset
                else -> continue
            } ?: continue
        val targetIdx = indexOfUnit(units[i] + branchOffset)
        if (targetIdx < 0) continue
        var downgrades = false
        var j = targetIdx
        while (j < insns.size && j < targetIdx + 8) {
            if (insns[j].opcode == Opcode.SGET_OBJECT) {
                downgrades = insns[j].fieldRef?.name == "FREE"
                break
            }
            j++
        }
        if (!downgrades || i + 1 >= insns.size) continue
        val goto = if (insn.codeUnitSize == 2) Opcode.GOTO_16 else Opcode.GOTO_32
        val replacement =
            Instruction.Branch0(Branch0Insn(goto.value.toUShort(), units[i + 1] - units[i]))
        if (replacement.codeUnitSize != insn.codeUnitSize) return false
        target.replaceInstruction(i, replacement)
        return true
    }
    return false
}

val unlockPremium =
    patch("Unlock Lifetime Premium") {
        description("Unlocks Premium-only features.")
        compatibleWith("com.shahzaman.pricetracker"("2.2.1"))
        dependsOn(removePairip)

        execute {
            isPremium.method.alwaysReturn(true)
            customerInfoIsPremiumActive.method.alwaysReturn(true)

            EntitlementInfo_isActive.method.alwaysReturn(true)

            if (!forcePremiumState(revenueCatStateUpdater)) {
                log.warn("Premium: state updater pattern not found; premium may stay locked.")
            }
            if (!forcePremiumState(playPurchaseCallback)) {
                log.warn("Premium: Play callback pattern not found; premium may stay locked.")
            }
        }
    }
