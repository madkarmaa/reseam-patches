package top.madkarma.patches.niagara.bugfixes

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

// Holder of the notification-channel diagnostics card. Found through the
// remote-config key it reads ("channel_multiplier"); the class builds a home
// feed card whose title is a dump of app hash codes (label hash, absolute
// package hash and absolute component hash for the first 30 apps, appended
// with no separator, so negative hashes show up as dashes).
val channelCardHolder = klass("channel card holder") {
    strings("channel_multiplier")
}

// Visibility gate of that card: takes the card type and returns a boolean,
// and unconditionally returns true, so once the card is registered during the
// post-restore app sync it stays on the home feed.
val channelCardGate = method("channel card gate") {
    inClass(channelCardHolder)
    returns(Type.Boolean)
}
