// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object Ultra : ExtClass("top.madkarma.picsart.ultra.Ultra") {
    val isUserSubscribed by static(returns = Type.Boolean)
    val accessLevel by static(returns = Type.Int)
    val tier by static(returns = "com.picsart.payment.api.subscription.tiers.domain.TierType")
    val addPremiumPermissions by static(Type.List, returns = Type.List)
}
