@file:Suppress("unused")

package top.madkarma.patches.alltrails.peak

import app.reseam.patch.patch

val unlockPeak = patch("Unlock Peak") {
    description("Unlocks Peak-only features.")
    compatibleWith("com.alltrails.alltrails")

    execute {
        userIsPro.method.alwaysReturn(true)
        log.info("Peak: ${userIsPro.descriptor} forced true.")

        userSubscriptionTier.method.alwaysReturn("peak")
        log.info("Peak: ${userSubscriptionTier.descriptor} forced to peak.")

        val peakName = resources.getString("peak_display_name")
            ?: error("Unlock Peak: peak_display_name string missing")

        if (!resources.setString(
                "peak_display_name",
                "$peakName | Patched with ❤ by MadKarma ;)",
            )
        ) {
            error("Unlock Peak: peak_display_name string not writable")
        }

        log.info("Peak: plan label tagged.")
    }
}
