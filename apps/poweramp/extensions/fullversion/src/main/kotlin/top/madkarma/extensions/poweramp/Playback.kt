// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Playback")
@file:Suppress("unused")

package top.madkarma.extensions.poweramp

import app.reseam.runtime.Export

// The native library keeps its own trial clock. About 20 days after install it rejects every
// playback pipeline built after the first one in a process, so resuming after an idle pause fails.
@Export
@Synchronized
fun allowPlayback() {
    System.loadLibrary("PowerampPlayback")
    if (!allowPlaybackNative())
        error(
            "Poweramp: disabling the native playback license check failed; see the PowerampPlayback log",
        )
}

private external fun allowPlaybackNative(): Boolean
