@file:Suppress("unused")

package top.madkarma.patches.shared

import app.reseam.patch.PatchRuntime
import app.reseam.patch.Target

// True when the target resolves in the current app. Catches only the
// missing-target failure; patch-time mutations outside the presence check
// still fail loudly.
fun PatchRuntime.isPresent(target: Target<*>): Boolean = try {
    explain(target)
    true
} catch (e: IllegalStateException) {
    false
}
