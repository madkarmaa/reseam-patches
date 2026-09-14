package top.madkarma.patches.scrobbler

import app.reseam.patch.*
import app.reseam.patch.dex.*

// LicenseState enum: UNKNOWN (initial), NO_LICENSE (receipt missing or rejected), VALID (licensed).
val licenseStateEnum = klass("license state enum") {
    strings("UNKNOWN", "NO_LICENSE", "VALID")
}

// Enum entry names are kept as const-strings in <clinit>; each is stored by
// the next sput of the enum type. Collects entry name -> field in one pass.
private fun enumEntries(enumDesc: String, clinit: Method): Map<String, FieldRef> {
    val entries = mutableMapOf<String, FieldRef>()
    var pendingName: String? = null

    for (insn in clinit.instructions) {
        insn.stringValue?.let { pendingName = it }
        if (insn.opcode != Opcode.SPUT_OBJECT) continue

        val field = insn.fieldRef ?: continue
        if (field.definingClass != enumDesc || field.fieldType != enumDesc) continue

        pendingName?.let { entries[it] = field }
        pendingName = null
    }

    return entries
}

// Points every NO_LICENSE load at the VALID entry instead.
private fun promoteToValid(
    bytecode: BytecodeScope,
    enumDesc: String,
    denied: FieldRef,
    valid: FieldRef,
): Int {
    var promoted = 0

    for (classDef in bytecode.classes) {
        for (target in classDef.methods) {
            target.instructions.forEachIndexed { index, insn ->
                if (insn.opcode != Opcode.SGET_OBJECT) return@forEachIndexed

                val field = insn.fieldRef ?: return@forEachIndexed
                if (field.definingClass != enumDesc || field.name != denied.name) return@forEachIndexed

                val dest = insn.regA ?: return@forEachIndexed
                val replacement = Instruction.RegField(
                    RegFieldInsn(
                        Opcode.SGET_OBJECT.value.toUShort(), dest.toUShort(), 0u, valid
                    ),
                )

                if (replacement.codeUnitSize != insn.codeUnitSize) {
                    error("Unlock Plus: replacement size mismatch in ${target.descriptor}")
                }
                target.replaceInstruction(index, replacement)

                promoted++
            }
        }
    }

    return promoted
}

val unlockPlus = patch("Unlock Plus") {
    description("Unlocks Plus-only features.")
    compatibleWith("com.arn.scrobble")

    execute {
        val enumDef = licenseStateEnum.classDef
        val enumDesc = enumDef.descriptor
        val clinit = enumDef.method("<clinit>") ?: error("Unlock Plus: $enumDesc has no <clinit>")

        val entries = enumEntries(enumDesc, clinit)
        val valid = entries["VALID"] ?: error("Unlock Plus: VALID entry not found in $enumDesc")
        val denied =
            entries["NO_LICENSE"] ?: error("Unlock Plus: NO_LICENSE entry not found in $enumDesc")

        val promoted = promoteToValid(bytecode, enumDesc, denied, valid)
        if (promoted == 0) error("Unlock Plus: no NO_LICENSE loads found")

        log.info("Plus: promoted $promoted NO_LICENSE load(s) to VALID ($enumDesc).")
    }
}
