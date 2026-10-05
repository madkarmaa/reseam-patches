// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val licenseState = klass("licenseState") {
    strings("NO_LICENSE", "VALID")
}

internal val licenseStateValueOf = licenseState.method("valueOf")

private val jwtPurchaseVerifier = methods("jwtPurchaseVerifier") {
    strings("pscrobbler_pro")
    returns(Type.Boolean)
}

private val playPurchaseVerifier = methods("playPurchaseVerifier") {
    strings("Error verifying purchase: ")
}

// Both billing variants evaluate receipts in a coroutine that takes an Object.
internal val checkLicense = method("checkLicense") {
    val verifier = (jwtPurchaseVerifier.all + playPurchaseVerifier.all).singleOrNull()
        ?: error("Unlock Plus: expected exactly one purchase verifier")

    calls(verifier)
    params(Type.Object)
}
