// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.iconpackstudio.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("ginlemon.iconpackstudio")

    execute {
        hasProAccess.alwaysReturn(true)
        log.info("Pro: ${hasProAccess.descriptor} grants access to editor, export, and backup features.")

        // LicenseStatus stores Pro access first and active subscription second.
        licenseStatusConstructor.before { param(0).assign(bool(true)) }
        log.info("Pro: ${licenseStatusConstructor.descriptor} publishes unlocked access to observers.")

        if (proLicenseBadge.method.replaceAllStrings(
                "PRO", "❤️"
            ) != 1
        ) error("Expected exactly one Pro license badge label")

        if (!resources.setStringEverywhere(
                "pro_license_verified", PATCH_ATTRIBUTION
            )
        ) error("Pro license attribution string missing")

        log.info("Pro: ${proLicenseBadge.descriptor} shows a heart badge and the shared patch attribution.")
    }
}
