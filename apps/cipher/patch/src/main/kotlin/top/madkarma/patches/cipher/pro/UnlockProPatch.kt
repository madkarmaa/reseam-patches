// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.cipher.pro

import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.cipher.CipherPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.universal.signature.signatureKiller

val unlockPro =
    patch(CipherPatchStrings.unlockProName) {
        description(CipherPatchStrings.unlockProDescription)
        compatibleWith("com.masum.cipher")
        // The integrity check and PDF export require the developer's signing certificate.
        dependsOn(signatureKiller)

        execute {
            hookedEnvironment.alwaysReturn(false)
            log.info("Pro: ${hookedEnvironment.descriptor} reports no hooking environment.")

            cachedEntitlement.alwaysReturn(true)
            log.info("Pro: ${cachedEntitlement.descriptor} reports an active entitlement.")

            cachedTier.alwaysReturn(LIFETIME_TIER)
            log.info("Pro: ${cachedTier.descriptor} returns the $LIFETIME_TIER tier.")

            if (storedSettingsMapper.method.replaceAllStrings(FREE_TIER, LIFETIME_TIER) != 1)
                error("Expected exactly one stored tier fallback")
            log.info(
                "Pro: ${storedSettingsMapper.descriptor} falls back to the $LIFETIME_TIER tier.",
            )

            userSettingsConstructor.after { thisObject.set(isProField, bool(true)) }
            log.info(
                "Pro: ${userSettingsConstructor.descriptor} sets ${isProField.name} to active Pro.",
            )

            // Without a Google Play order, the app would offer to migrate a legacy license.
            legacyProLicense.alwaysReturn(false)
            log.info("Pro: ${legacyProLicense.descriptor} reports no legacy license to migrate.")

            if (membershipCard.method.replaceAllStrings(PLAY_ACCOUNT_LABEL, PATCH_ATTRIBUTION) != 1)
                error("Expected exactly one Google Play account label")
            log.info(
                "Pro: ${membershipCard.descriptor} shows the patch attribution instead of the Google Play account line.",
            )

            if (
                proSettingsCard.method.replaceAllStrings(
                    PRO_FEATURES_LABEL + LIFETIME_ACCESS_LABEL,
                    PRO_FEATURES_LABEL + PATCH_ATTRIBUTION,
                ) != 1
            )
                error("Expected exactly one Lifetime Access label")
            log.info(
                "Pro: ${proSettingsCard.descriptor} shows the patch attribution as the plan summary.",
            )
        }
    }

private const val LIFETIME_TIER = "LIFETIME"
