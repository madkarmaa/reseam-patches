// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kwgt.pro

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal val checkProKey = method("checkProKey") {
    strings("Unable to check if pro package is installed")
}

internal val updateLicenseState = method("updateLicenseState") {
    strings("provider state changed to: ")
}

private val licenseState = klass("licenseState") {
    strings("NOT_CHECKED", "LICENSED", "NOT_LICENSED")
}

internal val licensedState = licenseState.field("LICENSED")

internal val proFeaturesAvailable = method("proFeaturesAvailable") {
    strings("skudebug")
    returns(Type.Boolean)
}
