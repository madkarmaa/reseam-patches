// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("HiddenPackagesApi28") @file:Suppress("unused")

package dev.secp192k1.universal.hidepackages

import android.content.pm.PackageManager
import app.reseam.runtime.Export

// Keep newer Android calls separate so older devices can verify the base class.
@Export
fun hasSigningCertificate(
    packageManager: PackageManager,
    packageName: String,
    certificate: ByteArray,
    type: Int,
): Boolean {
    return !isHidden(packageName) && packageManager.hasSigningCertificate(
        packageName,
        certificate,
        type,
    )
}

@Export(name = "hasSigningCertificateForUid")
fun hasSigningCertificate(
    packageManager: PackageManager,
    packageUid: Int,
    certificate: ByteArray,
    type: Int,
): Boolean {
    return !isEveryPackageHidden(
        packageManager,
        packageUid,
    ) && packageManager.hasSigningCertificate(packageUid, certificate, type)
}
