// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.googlephotos.spoof

import app.reseam.patch.Type
import app.reseam.patch.dex.Opcode
import app.reseam.patch.method
import app.reseam.patch.methods
import app.reseam.patch.points

internal val initializePixelFeatures = method("initializePixelFeatures") {
    strings("com.google.android.apps.photos.NEXUS_PRELOAD")
}

// Build reads are spread across the app. Seed from the opcode index, then narrow by field owner.
internal val deviceIdentityReads = methods("deviceIdentityReaders") {
    opcode(Opcode.SGET_OBJECT)
}.points("deviceIdentityReads") {
    opcode(Opcode.SGET_OBJECT)
    field {
        owner("android.os.Build")
        type(Type.String)
    }
}
