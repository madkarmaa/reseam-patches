// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("HiddenPackagesApi29") @file:Suppress("unused")

package dev.secp192k1.universal.hidepackages

import android.content.pm.PackageManager
import app.reseam.runtime.Export

// Keep newer Android calls separate so older devices can verify the base class.
@Export
fun isPackageSuspended(packageManager: PackageManager, packageName: String): Boolean {
    requireVisible(packageName)
    return packageManager.isPackageSuspended(packageName)
}
