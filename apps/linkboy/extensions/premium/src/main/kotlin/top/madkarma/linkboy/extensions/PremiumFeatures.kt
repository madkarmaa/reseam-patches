// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("PremiumFeatures")
@file:Suppress("unused")

package top.madkarma.linkboy.extensions

import app.reseam.runtime.Export
import top.madkarma.shared.attribution.PATCH_ATTRIBUTION

private const val PREMIUM_FEATURES = "string-array:premium_feature_list"

/**
 * Appends the shared attribution to the premium sheet's feature list, leaving every other string
 * array as is.
 */
@Export
fun withAttribution(resourceKey: String, items: List<String>): List<String> =
    if (resourceKey == PREMIUM_FEATURES) items + PATCH_ATTRIBUTION else items
