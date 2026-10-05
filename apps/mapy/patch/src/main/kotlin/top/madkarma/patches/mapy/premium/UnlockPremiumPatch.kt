// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.mapy.premium

import app.reseam.patch.*
import top.madkarma.patches.mapy.Premium
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPremium = patch("Unlock Premium") {
    description("Enables local Premium features and unlimited access, regardless of subscription updates.")
    compatibleWith("cz.seznam.mapy")

    execute {
        composeStringValueConstructor.before {
            paramOfType(Type.String).assign(
                call(
                    Premium.premiumTitle, paramOfType(Type.String), string(PATCH_ATTRIBUTION)
                )
            )
        }

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
        premiumFeatureGate.alwaysReturn(true)

        // Signed-out custom speeds have their own flow, independent of the feature gate.
        customSpeedConstructor.before {
            paramOfType(customSpeedReadOnlyFlow.returnType).assign(
                call(customSpeedReadOnlyFlow, call(customSpeedStateFlow, call(Premium.enabled)))
            )
        }
    }
}
