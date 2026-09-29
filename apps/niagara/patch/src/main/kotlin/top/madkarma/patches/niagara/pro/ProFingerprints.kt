package top.madkarma.patches.niagara.pro

import app.reseam.patch.Type
import app.reseam.patch.dex.Opcode
import app.reseam.patch.methods

// Holds the entitlement state (all features unlocked when the first flag is true).
// Several (Z,Z,Z) constructors store booleans; the genuine holder is the only
// non-synthetic one, so a plain predicate replaces ranking entirely.
//
// SYNTHETIC is set by the compiler - not the developer - on members it
// generates itself (default-arg overloads, bridges, desugared wrappers, ...).
val proCheckerCandidates = methods("pro checker constructor") {
    name("<init>")
    params(Type.Boolean, Type.Boolean, Type.Boolean)
    opcode(Opcode.IPUT_BOOLEAN)
}
