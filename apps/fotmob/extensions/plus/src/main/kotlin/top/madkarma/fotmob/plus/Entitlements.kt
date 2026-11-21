// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Entitlements")
@file:Suppress("unused")

package top.madkarma.fotmob.plus

import android.util.Log
import app.reseam.runtime.Export

/** The user's active entitlements: only [lifetimeEntitlement], built by the patch. */
@Export
fun activeEntitlements(lifetimeEntitlement: Any?): List<Any?> {
    Log.i("FotMobPlus", "Reporting a lifetime FotMob+ entitlement")
    return listOf(lifetimeEntitlement)
}
