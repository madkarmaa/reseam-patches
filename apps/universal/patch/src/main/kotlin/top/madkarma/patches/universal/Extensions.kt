// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.universal

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object SignatureKiller : ExtClass("bin.mt.signature.SignatureKiller") {
    val killSignature = static("killSignature", Type.String, Type.String)
    val killApkPath = static("killApkPath", Type.Context, Type.String)
    val checkSignatures = static("checkSignatures", Type.Context, Type.String, Type.String)
}
