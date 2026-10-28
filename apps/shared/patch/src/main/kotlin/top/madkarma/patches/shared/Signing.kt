// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.shared

import app.reseam.patch.PatchRuntime
import java.security.MessageDigest
import java.util.*

/** The lowercase SHA-1 of the input APK's original signing certificate, as GmsCore expects it. */
fun PatchRuntime.originalSignerSha1(): String {
    val certificate = files.signers().firstOrNull()
        ?: error("The input APK has no original v2/v3 signing certificate")

    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(certificate))
}
