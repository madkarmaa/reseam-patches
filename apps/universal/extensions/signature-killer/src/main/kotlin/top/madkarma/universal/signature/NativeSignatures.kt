// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("NativeSignatures")
@file:Suppress("unused")

package top.madkarma.universal.signature

import android.content.pm.PackageInfo
import android.os.Parcelable
import app.reseam.runtime.Export

@Export
fun initializeBeforeNativeLibrary(packageName: String, signature: String, libraryName: String) {
    val originalCreator = PackageInfo.CREATOR
    initializeBeforeNativeLibrary(packageName, signature, libraryName, false)
    check(installCreatorView(originalCreator)) {
        "Installing the native package creator view failed"
    }
}

private external fun installCreatorView(creator: Parcelable.Creator<PackageInfo>): Boolean
