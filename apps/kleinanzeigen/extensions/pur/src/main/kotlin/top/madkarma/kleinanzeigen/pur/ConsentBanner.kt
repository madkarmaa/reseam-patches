// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ConsentBanner") @file:Suppress("unused")

package top.madkarma.kleinanzeigen.pur

import app.reseam.runtime.Export

// The banner check is a suspend function, so its boolean answer is boxed.
@Export
fun notNeeded(): Any = false
