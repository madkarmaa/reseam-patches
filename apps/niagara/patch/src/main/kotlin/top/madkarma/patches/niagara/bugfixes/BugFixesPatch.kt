@file:Suppress("unused")

package top.madkarma.patches.niagara.bugfixes

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch

val bugFixes = patch("Bug fixes") {
    description("Fixes various app bugs.")
    compatibleWith("bitpit.launcher")

    execute {
        // Broken diagnostics card showing an unreadable app hash dump instead
        // of a message after restoring a backup.
        channelCardGate.alwaysReturn(false)
        log.info("Bug fixes: ${channelCardGate.descriptor} forced false.")

        // Future bug fixes go here.
    }
}
