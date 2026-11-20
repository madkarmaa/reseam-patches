// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SettingsSections")
@file:Suppress("unused")

package top.madkarma.geotracker.settings

import app.reseam.runtime.Export

/** The constants of [entries] except the one called [name], to build a new entries list from. */
@Export
fun without(entries: List<Enum<*>>?, name: String?): Array<Enum<*>> =
    entries.orEmpty().filter { it.name != name }.toTypedArray()
