// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.motioncam.recording

import app.reseam.patch.method

internal val cameraLibraryLoad = method("cameraLibraryLoad") {
    strings("native-camera-host")
    calls { owner("java.lang.System"); name("loadLibrary") }
}
