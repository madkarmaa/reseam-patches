// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.dex.Opcode
import app.reseam.patch.dex.buildInstructions
import app.reseam.patch.dex.fieldRef
import app.reseam.patch.dex.opcode
import app.reseam.patch.patch

val unlockPlus =
    patch("Unlock Plus") {
        description("Unlocks Plus-only features.")
        compatibleWith("com.arn.scrobble")

        execute {
            val denied = noLicense.ref
            val valid = validLicense.ref
            var promoted = 0

            // The API has no indexed field-reader query. Rewrite only reads, preserving the enum
            // initializer and reflective field access rather than aliasing the enum entries.
            for (classDef in bytecode.classes) {
                for (target in classDef.methods) {
                    for ((index, instruction) in target.instructions.withIndex()) {
                        if (instruction.opcode != Opcode.SGET_OBJECT || instruction.fieldRef != denied)
                            continue

                        val replacement =
                            buildInstructions { sgetObject(target.registerA(index), valid) }
                        target.replaceInstruction(index, replacement.single())
                        promoted++
                    }
                }
            }

            if (promoted == 0) error("Unlock Plus: no NO_LICENSE reads found")
            log.info("Plus: promoted $promoted NO_LICENSE read(s) to VALID.")
        }
    }
