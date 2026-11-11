// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.bugfixes

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.niagara.NiagaraPatchStrings

val bugFixes =
    patch(NiagaraPatchStrings.bugFixesName) {
        description(NiagaraPatchStrings.bugFixesDescription)
        compatibleWith("bitpit.launcher")

        execute {
            // Broken diagnostics card showing an unreadable app hash dump instead
            // of a message after restoring a backup.
            channelCardGate.alwaysReturn(false)
            log.info("Bug fixes: ${channelCardGate.descriptor} forced false.")
        }
    }
