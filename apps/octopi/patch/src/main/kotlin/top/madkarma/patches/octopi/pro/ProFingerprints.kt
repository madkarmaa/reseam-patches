// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.octopi.pro

import app.reseam.patch.Type
import app.reseam.patch.method

private val settingsHeader = method("settingsHeader") {
    strings("Octopi Launcher", " Pro")
}

internal val isPro = method("isPro") {
    calledBy(settingsHeader)
    params()
    returns(Type.Boolean)
}
