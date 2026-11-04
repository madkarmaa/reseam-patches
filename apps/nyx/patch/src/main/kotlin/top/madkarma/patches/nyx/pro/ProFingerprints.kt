// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.nyx.pro

import app.reseam.patch.Type
import app.reseam.patch.method

internal val hasActivePurchase = method("hasActivePurchase") {
    strings("has_active_purchase")
    returns(Type.Boolean)
}
