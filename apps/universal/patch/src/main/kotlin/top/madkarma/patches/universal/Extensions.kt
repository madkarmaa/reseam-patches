// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object SignatureKiller : ExtClass("bin.mt.signature.SignatureKiller") {
    val initialize by static(Type.Context, Type.String, Type.String, Type.Boolean)
    val killSignature by static(Type.String, Type.String)
    val killApkPath by static(Type.Context, Type.String)
    val checkSignatures by static(Type.Context, Type.String, Type.String)
}
