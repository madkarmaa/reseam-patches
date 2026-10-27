// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.shared

import app.reseam.patch.ResourceScope

// setString rewrites only the first value it finds, so other translations and language splits keep
// the original text. Point the string in every configuration of every component at the new value.
fun ResourceScope.setStringEverywhere(name: String, value: String): Boolean {
    var found = false

    for (componentName in components()) {
        val resources = component(componentName)
        val id = resources.id("string", name) ?: continue
        val index = resources.poolAdd(value) ?: error("Cannot add $name to $componentName")

        resources.replaceEntry(id, index)
        found = true
    }

    return found
}
