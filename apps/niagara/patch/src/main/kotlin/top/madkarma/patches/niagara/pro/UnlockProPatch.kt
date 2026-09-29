@file:Suppress("unused")

package top.madkarma.patches.niagara.pro

import app.reseam.patch.before
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.isSet
import app.reseam.patch.patch
import top.madkarma.patches.niagara.weather.weatherWidgetFix

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro features.")
    compatibleWith("bitpit.launcher")
    dependsOn(weatherWidgetFix)

    execute {
        val proCheckerConstructor =
            proCheckerCandidates.single { !AccessFlags.SYNTHETIC.isSet(method.info.accessFlags) }

        proCheckerConstructor.before {
            param(0).assign(bool(true))
        }

        val thankYou = resources.getString("purchase_pro_thank_you")
            ?: error("Unlock Pro: purchase_pro_thank_you string missing")

        if (!resources.setString(
                "purchase_pro_thank_you",
                "$thankYou\n\nPatched with ❤ by MadKarma ;)",
            )
        ) {
            error("Unlock Pro: purchase_pro_thank_you string not writable")
        }
        log.info("Pro: thank-you label tagged.")

        log.info("Pro: unlocked via ${proCheckerConstructor.descriptor}.")

    }
}
