package top.madkarma.patches.niagara

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.isSet
import app.reseam.patch.methods
import app.reseam.patch.patch

// Holds the entitlement state (all features unlocked when the first flag is true).
// Several (Z,Z,Z) constructors store booleans; the genuine holder is the only
// non-synthetic one, so a plain predicate replaces ranking entirely.
//
// SYNTHETIC is set by the compiler — not the developer — on members it
// generates itself (default-arg overloads, bridges, desugared wrappers, ...).
private val proCheckerCandidates = methods("pro checker constructor") {
    name("<init>")
    params(Type.Boolean, Type.Boolean, Type.Boolean)
    opcode(Opcode.IPUT_BOOLEAN)
}

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features.")
    compatibleWith("bitpit.launcher")

    execute {
        val proCheckerConstructor =
            proCheckerCandidates.single { !AccessFlags.SYNTHETIC.isSet(method.info.accessFlags) }

        proCheckerConstructor.before {
            param(0).assign(bool(true))
        }

        val thankYou = resources.getString("purchase_pro_thank_you")
            ?: error("Unlock Pro: purchase_pro_thank_you string missing")

        if (!resources.setString(
                "purchase_pro_thank_you",
                "$thankYou\n\nPatched with ❤ by MadKarma ;)",
            )
        ) {
            error("Unlock Pro: purchase_pro_thank_you string not writable")
        }

        log.info("Pro: unlocked via ${proCheckerConstructor.descriptor}.")
    }
}
