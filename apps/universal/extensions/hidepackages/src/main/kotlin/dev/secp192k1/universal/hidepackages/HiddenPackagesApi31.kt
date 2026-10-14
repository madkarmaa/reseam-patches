// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package dev.secp192k1.universal.hidepackages

import android.content.pm.PackageManager
import app.reseam.runtime.Export
import dev.secp192k1.universal.hidepackages.HiddenPackages.requireVisible

// Keep newer Android calls separate so older devices can verify the base class.
object HiddenPackagesApi31 {
    @Export
    @JvmStatic
    fun getTargetSdkVersion(packageManager: PackageManager, packageName: String): Int {
        requireVisible(packageName)
        return packageManager.getTargetSdkVersion(packageName)
    }
}
