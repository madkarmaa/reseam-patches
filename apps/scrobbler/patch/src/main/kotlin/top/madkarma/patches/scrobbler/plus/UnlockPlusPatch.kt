// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.patch

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
