@file:Suppress("unused")

package top.madkarma.patches.universal

import top.madkarma.revisions.recordedPatch

val forceExtractNativeLibs = recordedPatch("Force extract native libs") {
    description("Sets android:extractNativeLibs to true when it is false.")
    enabledByDefault(true)

    execute {
        manifest.edit {
            val application = findByTag("application").firstOrNull()
                ?: error("ExtractNativeLibs: manifest has no <application> element")

            when (application["android:extractNativeLibs"]?.lowercase()) {
                "false" -> {
                    application["android:extractNativeLibs"] = "true"
                    log.info("ExtractNativeLibs: flipped to true.")
                }

                "true" -> log.info("ExtractNativeLibs: already true, skipped.")
                null -> log.info("ExtractNativeLibs: attribute absent, skipped.")
                else -> log.info("ExtractNativeLibs: unexpected value, skipped.")
            }
        }

    }
}
