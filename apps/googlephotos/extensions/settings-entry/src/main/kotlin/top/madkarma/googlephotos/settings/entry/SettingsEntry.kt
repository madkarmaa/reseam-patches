// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SettingsEntry")
@file:Suppress("unused")

package top.madkarma.googlephotos.settings.entry

import app.reseam.runtime.Export

@Volatile private var reseamRow: Any? = null

/** Keeps the Reseam settings row the patch builds from Photos' settings item at app start. */
@Export
fun rememberReseamRow(row: Any) {
    reseamRow = row
}

/** The main menu's rows with the Reseam row first, or null for the empty loading menu. */
@Export
fun withReseamRow(rows: List<Any?>): List<Any?>? {
    val row = reseamRow ?: return null
    if (rows.isEmpty()) return null
    return listOf(row) + rows
}
