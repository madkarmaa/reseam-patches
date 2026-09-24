@file:Suppress("unused")

package top.madkarma.patches.universal

import app.reseam.patch.ExtClass
import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.appEntry
import top.madkarma.revisions.recordedPatch

object UpdateChecker : ExtClass("top.madkarma.universal.extensions.UpdateChecker") {
    val check = static("check", Type.Context, Type.String, Type.String)
}

val notifyAppUpdates = recordedPatch("Notify app updates") {
    description("Shows a popup when the applied patches have updates.")
    enabledByDefault(true)

    execute {
        manifest.addPermission("android.permission.INTERNET")

        val packageName =
            manifest.packageName ?: error("UpdateChecker: manifest has no package name")

        appEntry.after {
            call(
                UpdateChecker.check,
                thisObject,
                string("https://github.com/madkarmaa/reseam-patches/releases/latest/download/revisions.json"),
                string(packageName)
            )
        }

    }
}
