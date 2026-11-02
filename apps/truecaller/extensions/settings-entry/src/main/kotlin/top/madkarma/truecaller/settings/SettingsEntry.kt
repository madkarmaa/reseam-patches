// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SettingsEntry") @file:Suppress("unused")

package top.madkarma.truecaller.settings

import android.view.View
import app.reseam.runtime.Export
import app.reseam.runtime.Reseam

@Export
fun bind(root: View, rowId: Int) {
    val row = checkNotNull(root.findViewById(rowId)) { "Reseam settings row is missing" }
    row.setOnClickListener { Reseam.bundle("madkarma-patches").openSettings() }
}
