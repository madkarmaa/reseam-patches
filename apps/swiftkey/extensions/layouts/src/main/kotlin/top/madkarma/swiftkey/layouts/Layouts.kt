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

/**
 * [layouts] followed by the legacy layouts that its redesigned layouts replaced. The app names a
 * redesigned layout after the one it replaced, with a `_V2` suffix, and keeps the old one.
 */
@Export
fun withLegacyLayouts(layouts: List<*>): List<*> {
    val layout = layouts.firstOrNull() as? Enum<*> ?: return layouts
    val layoutsByName = layout.declaringJavaClass.enumConstants.associateBy { it.name }

    val legacy =
        layouts
            .map { (it as Enum<*>).name }
            .filter { it.endsWith("_V2") }
            .mapNotNull { layoutsByName[it.removeSuffix("_V2")] }
            .filterNot { it in layouts }
    if (legacy.isEmpty()) return layouts

    return layouts + legacy
}
