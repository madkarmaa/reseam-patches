// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.adguard.lifetime

import app.reseam.patch.*
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockLifetime = patch("Unlock Lifetime premium") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.adguard.android")

    execute {
        initialCachedState.before { returnValue(lifetimeLicense()) }
        log.info("Lifetime: ${initialCachedState.descriptor} returns a Family lifetime license.")

        fetchBackendState.before { returnValue(lifetimeLicense()) }
        log.info("Lifetime: ${fetchBackendState.descriptor} returns a Family lifetime license.")

        // Keep the updater's storage writes and notifications using the normalized state.
        updateLicenseState.before { param(0).assign(lifetimeLicense()) }
        log.info("Lifetime: ${updateLicenseState.descriptor} stores the normalized lifetime license.")

        maskLicenseKey.replace { returnValue(paramOfType(Type.String)) }
        log.info("Lifetime: ${maskLicenseKey.descriptor} displays the full license label.")
    }
}

private fun CodeScope.lifetimeLicense(): ValueRef = newInstance(
    paidLicenseState.descriptor,
    paidLicenseConstructor.proto,
    string(PATCH_ATTRIBUTION), // License key
    staticField(familyLicense),
    staticField(lifetimeInstance),
    int(67), // Devices in use.
    int(69), // Family license device limit.
    string("You (maybe)"), // Account owner.
)
