// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Layouts")
@file:Suppress("unused")

package top.madkarma.swiftkey.layouts

import app.reseam.runtime.Export

/**
 * Every keyboard layout the app defines, given some of them. [layouts] holds constants of the app's
 * layout enum; an empty list stays as it is.
 */
@Export
fun everyLayout(layouts: List<*>): List<*> {
    val layout = layouts.firstOrNull() as? Enum<*> ?: return layouts
    return layout.declaringJavaClass.enumConstants.toList()
}
