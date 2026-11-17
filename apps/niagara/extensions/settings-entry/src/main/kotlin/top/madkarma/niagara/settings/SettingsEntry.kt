// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SettingsEntry")
@file:Suppress("unused")

package top.madkarma.niagara.settings

import android.view.View
import android.widget.ImageView
import app.reseam.runtime.Export
import java.lang.ref.WeakReference

// Niagara rebuilds its settings rows for every screen, so only the newest row is tracked.
private var entryRow = WeakReference<Any>(null)

@Export
fun rememberRow(row: Any?) {
    entryRow = WeakReference(row)
}

/** Whether [row] is the added entry row, which opens the Reseam settings. */
@Export fun isEntry(row: Any?): Boolean = row != null && row === entryRow.get()

/**
 * Shows [iconRes] in [icon] when [row] is the added entry row. Its binder hides the icon frame of a
 * row without an icon, so the frame is shown again.
 */
@Export
fun showIcon(row: Any?, icon: ImageView?, iconRes: Int) {
    if (icon == null || !isEntry(row)) return

    icon.setImageResource(iconRes)
    (icon.parent as? View)?.visibility = View.VISIBLE
}
