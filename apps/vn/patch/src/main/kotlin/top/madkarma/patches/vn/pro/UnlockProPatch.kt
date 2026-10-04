// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vn.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features")
    compatibleWith("com.frontrow.vlog")

    execute {
        proGate.alwaysReturn(true)
        log.info("Pro: ${proGate.descriptor} forced true.")
    }
}
