// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mapy.premium

import app.reseam.patch.*
import top.madkarma.extensions.mapy.bindings.Premium
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPremium = patch("Unlock Premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("cz.seznam.mapy")

    execute {
        composeStringValueConstructor.before {
            paramOfType(Type.String).assign(
                call(
                    Premium.premiumTitle, paramOfType(Type.String), string(PATCH_ATTRIBUTION)
                )
            )
        }
        log.info("Premium: ${composeStringValueConstructor.descriptor} adds attribution to premium titles.")

        val supportedTypes = setOf(
            Type.Boolean,
            limitedOfflineMaps.classDef.interfaces.single(),
            limitedFeatureAccess.classDef.interfaces.single()
        )
        if (premiumFeaturesConstructor.parameterTypes.any { it !in supportedTypes }) error("Unsupported PremiumFeatures parameter types")

        // Normalize initial defaults and refreshed server features before callers receive them.
        premiumFeaturesConstructor.after {
            call(
                Premium.unlockFeatures,
                thisObject,
                staticField(unlimitedOfflineMapsInstance),
                staticField(unlimitedFeatureAccessInstance)
            )
        }
        log.info("Premium: ${premiumFeaturesConstructor.descriptor} enables premium features and unlimited offline maps.")

        premiumFeatureGate.alwaysReturn(true)
        log.info("Premium: ${premiumFeatureGate.descriptor} grants access before account data is available.")

        // Signed-out custom speeds have their own flow, independent of the feature gate.
        customSpeedConstructor.before {
            paramOfType(customSpeedReadOnlyFlow.returnType).assign(
                call(customSpeedReadOnlyFlow, call(customSpeedStateFlow, call(Premium.enabled)))
            )
        }
        log.info("Premium: ${customSpeedConstructor.descriptor} enables custom speeds while signed out.")
    }
}
