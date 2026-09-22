@file:Suppress("unused")

package top.madkarma.patches.universal

import app.reseam.patch.*

object UpdateChecker : ExtClass("top.madkarma.universal.extensions.UpdateChecker") {
    val check = static("check", Type.Context, Type.String, Type.String, Type.String)
}

val notifyAppUpdates = patch("Notify app updates") {
    description("Shows a popup if the patches support a newer version of the app.")
    enabledByDefault(true)

    execute {
        manifest.addPermission("android.permission.INTERNET")

        val packageName =
            manifest.packageName ?: error("UpdateChecker: manifest has no package name")
        val installedVersion =
            manifest.versionName ?: error("UpdateChecker: manifest has no version name")

        appEntry.after {
            call(
                UpdateChecker.check,
                thisObject,
                string("https://github.com/madkarmaa/reseam-patches/releases/latest/download/patches.json"),
                string(installedVersion),
                string(packageName)
            )
        }
    }
}
