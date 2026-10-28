// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.shared

import app.reseam.patch.XmlDocument

// Android 6 and later grant uses-permission-sdk-23 like uses-permission, and Reseam supports Android 8 onwards.
private val PERMISSION_REQUESTS = setOf("uses-permission", "uses-permission-sdk-23")

/** Whether the manifest already requests `permission`. */
fun XmlDocument.hasPermission(permission: String) =
    root.children.any { it.tag in PERMISSION_REQUESTS && it["android:name"] == permission }
