// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.smartlauncher.lifetime

import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.universal.signature.signatureKiller
import top.madkarma.smartlauncher.extensions.bindings.PreferenceOverrides

val unlockLifetime = patch("Unlock Lifetime premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("ginlemon.flowerfree")
    dependsOn(signatureKiller)

    execute {
        log.info("Lifetime: ${lifetimePreferenceInitializer.descriptor} defines $LIFETIME_PREFERENCE.")

        readPreference.before {
            val override = call(
                PreferenceOverrides.enabledOverride,
                thisObject.call(preferenceName),
                string(LIFETIME_PREFERENCE),
            )
            whenNotNull(override) {
                returnValue(override)
            }
        }
        log.info("Lifetime: ${readPreference.descriptor} reports lifetime access for $LIFETIME_PREFERENCE.")

        writePreference.before {
            val override = call(
                PreferenceOverrides.enabledOverride,
                thisObject.call(preferenceName),
                string(LIFETIME_PREFERENCE),
            )
            whenNotNull(override) {
                paramOfType(Type.Object).assign(override)
            }
        }
        log.info("Lifetime: ${writePreference.descriptor} preserves lifetime access when preferences change.")

        if (fullLicenseRow.method.replaceAllStrings(
                "SL6", "❤"
            ) != 1
        ) error("Expected exactly one full-license badge label")

        if (!resources.setStringEverywhere(
                "full_license_thank_you", PATCH_ATTRIBUTION
            )
        ) error("Full-license attribution string missing")

        log.info("Lifetime: ${fullLicenseRow.descriptor} shows a heart badge and the shared patch attribution.")
    }
}
