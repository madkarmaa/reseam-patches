// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Ads") @file:Suppress("unused")

package top.madkarma.kleinanzeigen.ads

import app.reseam.runtime.Export

@Export
fun clearPlacements(placements: MutableMap<*, *>?) {
    placements?.clear()
}
