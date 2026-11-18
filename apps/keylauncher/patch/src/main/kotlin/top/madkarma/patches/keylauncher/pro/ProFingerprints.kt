// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.keylauncher.pro

import app.reseam.patch.Type
import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

// The purchase writer also stores this key, but returns nothing.
internal val isProPurchased =
    method("isProPurchased") {
        strings("is_pro_purchased")
        returns(Type.Boolean)
    }

// Billing period enums share some of these names; only the pro type has all four.
private val proType =
    klass("proType") {
        enumConstants("NONE", "MONTHLY", "YEARLY", "LIFETIME")
    }

internal val lifetimeProType = proType.enumConstant("LIFETIME")

internal val storedProType =
    method("storedProType") {
        strings("pro_type")
        returns(proType.descriptor)
    }
