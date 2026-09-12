package top.madkarma.patches.niagara.pro

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.isSet
import app.reseam.patch.dex.opcode
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.bypassSignatureChecks

// Holds the entitlement state (all features unlocked when the first flag is true).
// Public (Z,Z,Z) constructor running INVOKE_DIRECT, three IPUT_BOOLEAN and nothing
// else, on a plain Object subclass with a single direct method, three
// virtual methods and three final boolean fields. The query narrows to the
// few matching constructors; rankBy scores the full shape so the engine
// itself picks the unique winner and reports near-misses on failure.
private val CONSTRUCTOR_OPCODES = listOf(
    Opcode.INVOKE_DIRECT,
    Opcode.IPUT_BOOLEAN,
    Opcode.IPUT_BOOLEAN,
    Opcode.IPUT_BOOLEAN,
    Opcode.RETURN_VOID,
)

val proCheckerConstructor = method("pro checker constructor") {
    name("<init>")
    params(Type.Boolean, Type.Boolean, Type.Boolean)
    opcode(Opcode.INVOKE_DIRECT, Opcode.IPUT_BOOLEAN, Opcode.RETURN_VOID)
    rankBy("pro checker shape") {
        var score = 0

        if (AccessFlags.PUBLIC.isSet(method.info.accessFlags)) score += 1
        if (AccessFlags.CONSTRUCTOR.isSet(method.info.accessFlags)) score += 1

        if (method.instructions.map { it.opcode } == CONSTRUCTOR_OPCODES) score += 2

        val classDef = method.classDef

        if (classDef.superclass == Type.Object) score += 1
        if (classDef.directMethods.size == 1) score += 1
        if (classDef.virtualMethods.size == 3) score += 1

        if (classDef.instanceFields.size == 3 && classDef.instanceFields.all {
                it.fieldType == Type.Boolean && AccessFlags.FINAL.isSet(it.accessFlags)
            }) {
            score += 2
        }

        score
    }
}

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features.")
    compatibleWith("bitpit.launcher")
    dependsOn(bypassSignatureChecks)

    execute {
        proCheckerConstructor.before {
            param(0).assign(bool(true))
        }

        log.info("Pro: unlocked via ${proCheckerConstructor.descriptor}.")
    }
}
