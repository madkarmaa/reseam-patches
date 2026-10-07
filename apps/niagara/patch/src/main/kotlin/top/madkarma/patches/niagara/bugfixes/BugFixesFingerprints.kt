// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.bugfixes

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val channelCardHolder = klass("channelCardHolder") {
    strings("channel_multiplier")
}

// The card's only Boolean method is its visibility gate; the method itself has no strings.
internal val channelCardGate = method("channelCardGate") {
    inClass(channelCardHolder)
    returns(Type.Boolean)
}
