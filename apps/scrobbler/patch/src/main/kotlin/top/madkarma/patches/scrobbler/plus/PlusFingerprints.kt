package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.BytecodeScope
import app.reseam.patch.dex.*
import app.reseam.patch.klass
import app.reseam.patch.types.FieldRef
import app.reseam.patch.types.Instruction
import app.reseam.patch.types.RegFieldInsn

// LicenseState enum: UNKNOWN (initial), NO_LICENSE (receipt missing or rejected), VALID (licensed).
val licenseStateEnum = klass("license state enum") {
    strings("NO_LICENSE")
}

// Enum entry names are kept as const-strings in <clinit>; each is stored by
// the next sput of the enum type. Collects entry name -> field in one pass.
fun enumEntries(enumDesc: String, clinit: Method): Map<String, FieldRef> {
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
fun promoteToValid(
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
