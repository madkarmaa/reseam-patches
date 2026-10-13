// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.*

internal val licenseState = klass("licenseState") {
    enumConstants("VALID")
}

internal val validLicense = licenseState.enumConstant("VALID")

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
