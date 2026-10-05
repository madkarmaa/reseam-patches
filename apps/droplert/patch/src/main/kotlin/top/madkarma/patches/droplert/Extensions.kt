// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.droplert

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object Prefs : ExtClass("top.madkarma.droplert.extensions.Prefs") {
    val putBoolean by static(Type.Context, Type.String, Type.Boolean)
}
