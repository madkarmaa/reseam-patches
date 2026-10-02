// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.adguard.lifetime

import app.reseam.patch.CodeScope
import app.reseam.patch.Type
import app.reseam.patch.ValueRef
import app.reseam.patch.before
import app.reseam.patch.invoke
import app.reseam.patch.patch
import app.reseam.patch.replace

val unlockLifetime =
    patch("Unlock Lifetime premium") {
        description("Unlocks premium-only features.")
        compatibleWith("com.adguard.android"("4.15.0"))

        execute {
            initialCachedState.before { returnValue(lifetimeLicense()) }
            fetchBackendState.before { returnValue(lifetimeLicense()) }

            // Keep the updater's storage writes and notifications using the normalized state.
            updateLicenseState.before { param(0).assign(lifetimeLicense()) }

            maskLicenseKey.replace { returnValue(paramOfType(Type.String)) }
        }
    }

private fun CodeScope.lifetimeLicense(): ValueRef =
    newInstance(
        paidLicenseState.descriptor,
        paidLicenseConstructor.proto,
        string("Patched with ❤ by MadKarma ;)"), // License key
        staticField(familyLicense),
        staticField(lifetimeInstance),
        int(67), // Devices in use.
        int(69), // Family license device limit.
        string("You (maybe)"), // Account owner.
    )
