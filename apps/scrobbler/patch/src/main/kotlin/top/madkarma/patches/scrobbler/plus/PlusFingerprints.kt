// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.scrobbler.plus

import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.point

private val licenseState =
    klass("licenseState") {
        strings("NO_LICENSE")
    }

internal val noLicense = licenseEntry("NO_LICENSE")
internal val validLicense = licenseEntry("VALID")

// Field declarations lose the enum names; the initializer preserves each name before its field assignment.
private fun licenseEntry(name: String) =
    licenseState
        .method("<clinit>")
        .point { string(name) }
        .next { field { type(licenseState.descriptor) } }
        .field(name)
