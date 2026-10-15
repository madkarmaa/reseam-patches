// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SignatureChecksApi33") @file:Suppress("unused")

package top.madkarma.universal.signature

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.reseam.runtime.Export

// Keep the API 33 parameter type out of the class initialized on older devices.
@Export
fun getPackageArchiveInfoWithFlags(
    manager: PackageManager,
    archiveFilePath: String,
    flags: PackageManager.PackageInfoFlags,
): PackageInfo? = spoofInstalledArchive(
    manager,
    archiveFilePath,
    manager.getPackageArchiveInfo(archiveFilePath, flags),
)
