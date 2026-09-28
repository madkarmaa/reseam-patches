@file:Suppress("unused")

package top.madkarma.patches.universal

import app.reseam.patch.patch

val forceExtractNativeLibs = patch("Force extract native libs") {
    description("Sets android:extractNativeLibs to true when it is false.")
    enabledByDefault(true)

    execute {
        var flipped = false
        manifest.edit {
            val application = findByTag("application").firstOrNull()
                ?: error("ExtractNativeLibs: manifest has no <application> element")

            if (application["android:extractNativeLibs"]?.lowercase() == "false") {
                application["android:extractNativeLibs"] = "true"
                flipped = true
            }
        }
        if (flipped) log.info("ExtractNativeLibs: extractNativeLibs flipped to true.")
        else log.info("ExtractNativeLibs: extractNativeLibs already true, nothing to do.")
    }
}
