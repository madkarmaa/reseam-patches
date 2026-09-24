@file:Suppress("unused")

package top.madkarma.patches.niagara

import app.reseam.patch.*
import top.madkarma.revisions.recordedPatch

// Holder of the notification-channel diagnostics card. Found through the
// remote-config key it reads ("channel_multiplier"); the class builds a home
// feed card whose title is a dump of app hash codes (label hash, absolute
// package hash and absolute component hash for the first 30 apps, appended
// with no separator, so negative hashes show up as dashes).
private val channelCardHolder = klass("channel card holder") {
    strings("channel_multiplier")
}

// Visibility gate of that card: takes the card type and returns a boolean,
// and unconditionally returns true, so once the card is registered during the
// post-restore app sync it stays on the home feed.
private val channelCardGate = method("channel card gate") {
    inClass(channelCardHolder)
    returns(Type.Boolean)
}

val bugFixes = recordedPatch("Bug fixes") {
    description("Fixes various app bugs.")
    compatibleWith("bitpit.launcher")

    execute {
        // Broken diagnostics card showing an unreadable app hash dump instead
        // of a message after restoring a backup.
        channelCardGate.alwaysReturn(false)
        log.info("Channel hash card suppressed via ${channelCardGate.descriptor}.")

        // Future bug fixes go here.

    }
}
