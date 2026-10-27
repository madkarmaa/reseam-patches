// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.region

import app.reseam.patch.*
import top.madkarma.patches.capcut.integrity.bypassIntegrityChecks

val bypassGeographicRestrictions = patch("Bypass geographic restrictions") {
    description("Keeps locally blocked tools, pages, and links accessible.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(bypassIntegrityChecks)

    execute {
        for (blockList in geographicBlockLists) {
            blockList.replace {
                returnValue(
                    callStatic(
                        "java.util.Collections", "emptyList", proto("java.util.List")
                    )
                )
            }
            log.info("Region: ${blockList.descriptor} returns an empty geographic block list.")
        }
    }
}

val bypassEffectsRegionRestrictions = patch("Bypass effects region restrictions") {
    description("Keeps locally blocked effects accessible. Server restrictions may still apply.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(bypassIntegrityChecks)

    execute {
        effectPlatform.before {
            paramOfType(Type.String).assign(string("windows"))
        }
        log.info("Region: ${effectPlatform.descriptor} sets the effects request platform to windows.")

        initialEffectDeviceIdentifier.before {
            paramOfType(Type.String).assign(string("0"))
        }
        log.info("Region: ${initialEffectDeviceIdentifier.descriptor} initializes the effects device identifier to zero.")

        // The SDK exposes a later update path even though this build has no static callers.
        updatedEffectDeviceIdentifier.before {
            paramOfType(Type.String).assign(string("0"))
        }
        log.info("Region: ${updatedEffectDeviceIdentifier.descriptor} keeps later effects device identifier updates at zero.")
    }
}
