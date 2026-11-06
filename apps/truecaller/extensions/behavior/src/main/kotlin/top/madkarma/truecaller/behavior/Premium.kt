// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Premium")
@file:Suppress("unused")

package top.madkarma.truecaller.behavior

import app.reseam.runtime.Export
import com.truecaller.premium.data.tier.PremiumTierType

@Export fun tier(): PremiumTierType = PremiumTierType.PREMIUM
