// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SettingsEntry")
@file:Suppress("unused")

package top.madkarma.niagara.settings

import app.reseam.runtime.Export
import app.reseam.runtime.Reseam
import java.lang.ref.WeakReference

private const val BUNDLE = "madkarma-patches"

// Niagara rebuilds its settings rows for every screen, so only the newest row is tracked.
private var entryRow = WeakReference<Any>(null)

@Export
fun rememberRow(row: Any?) {
    entryRow = WeakReference(row)
}

/** Opens the Reseam settings when [row] is the added entry row. */
@Export
fun openIfEntry(row: Any?): Boolean {
    if (row == null || row !== entryRow.get()) return false

    Reseam.bundle(BUNDLE).openSettings()
    return true
}
