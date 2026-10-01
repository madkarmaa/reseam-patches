// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.universal.signature

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object SignatureKiller : ExtClass("bin.mt.signature.SignatureKiller") {
    val killSignature = static("killSignature", Type.String, Type.String)
    val killApkPath = static("killApkPath", Type.Context, Type.String)
    val checkSignatures = static("checkSignatures", Type.Context, Type.String, Type.String)
}

val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

val patchClassLoader = object {}.javaClass.classLoader
