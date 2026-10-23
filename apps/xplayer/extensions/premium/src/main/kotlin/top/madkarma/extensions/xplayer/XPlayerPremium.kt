// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("XPlayerPremium") @file:Suppress("unused")

package top.madkarma.extensions.xplayer

import app.reseam.runtime.Export

@Export
fun isPremiumPreference(key: String?): Boolean = key == "adRemoved"
