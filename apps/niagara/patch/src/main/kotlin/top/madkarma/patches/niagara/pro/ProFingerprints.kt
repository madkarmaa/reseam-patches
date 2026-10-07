// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.pro

import app.reseam.patch.Type
import app.reseam.patch.method

private val decodeSession = method("decodeSession") {
    strings("session missing")
}

// The session decoder constructs the entitlement's three Boolean flags.
internal val proCheckerConstructor = method("proCheckerConstructor") {
    calledBy(decodeSession)
    params(Type.Boolean, Type.Boolean, Type.Boolean)
}
