// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.iconpackstudio.pro

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val purchaseRepository = klass("purchaseRepository") {
    strings("iconstudio.fx.pack1")
}

internal val hasProAccess = method("hasProAccess") {
    inClass(purchaseRepository)
    returns(Type.Boolean)
}

private val licenseStatus = klass("licenseStatus") {
    strings("LicenseStatus(hasProAccess=")
}

internal val licenseStatusConstructor = licenseStatus.method("<init>")
