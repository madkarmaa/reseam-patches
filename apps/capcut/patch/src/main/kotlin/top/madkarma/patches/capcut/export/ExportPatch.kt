// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.export

import app.reseam.patch.*
import top.madkarma.patches.capcut.premium.unlockPremium

val unlockUltraHdExport = patch("Unlock Ultra HD export") {
    description("Unlocks editor 2K/4K and template Ultra HD export.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(unlockPremium)

    execute {
        resolutionRequiresPurchase.alwaysReturn(false)
        log.info("UHD: ${resolutionRequiresPurchase.descriptor} no longer requires purchasing a resolution benefit.")

        // The export-success callback also uses this fallback to update the next selected resolution.
        resolutionFallback.replace { returnValue(paramOfType(Type.Int)) }
        log.info("UHD: ${resolutionFallback.descriptor} preserves the requested resolution before and after export.")

        templateUltraHdEntitlement.alwaysReturn(true)
        log.info("UHD: ${templateUltraHdEntitlement.descriptor} accepts the local template UHD entitlement.")

        templateUltraHdAvailable.alwaysReturn(true)
        log.info("UHD: ${templateUltraHdAvailable.descriptor} exposes the existing template UHD control.")

        // This entry only authorizes the checkbox. Its callers still enforce draft/device support.
        templateUltraHdUseCheck.replace {
            val accepted = callStatic(
                "java.lang.Boolean", "valueOf", proto("java.lang.Boolean", Type.Boolean), bool(true)
            )

            paramOfType("kotlin.jvm.functions.Function1").callInterface(
                "kotlin.jvm.functions.Function1",
                "invoke",
                proto(Type.Object, Type.Object),
                accepted,
            )

            returnVoid()
        }
        log.info("UHD: ${templateUltraHdUseCheck.descriptor} continues the template UHD selection callback.")
    }
}
