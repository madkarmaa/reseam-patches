// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mapy

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object Premium : ExtClass("top.madkarma.extensions.mapy.Premium") {
    val unlockFeatures by static(Type.Object, Type.Object, Type.Object)
    val enabled by static(returns = Type.Object)
    val premiumTitle by static(Type.String, Type.String, returns = Type.String)
}
