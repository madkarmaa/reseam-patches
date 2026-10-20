// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.shared

import app.reseam.patch.PatchRuntime
import app.reseam.patch.Target

// The SDK reports missing and ambiguous targets as IllegalStateException.
// Only missing targets select a fallback; ambiguity and other failures must abort.
fun PatchRuntime.isPresent(target: Target<*>): Boolean = try {
    explain(target)
    true
} catch (e: IllegalStateException) {
    val message = e.message.orEmpty()

    if (
        !message.startsWith("No method matched '") &&
        !message.startsWith("No class matched '") &&
        !message.startsWith("Class not found: ")
    ) throw e

    false
}
