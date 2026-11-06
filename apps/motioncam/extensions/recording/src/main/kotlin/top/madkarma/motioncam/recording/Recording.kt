// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Recording")

package top.madkarma.motioncam.recording

import app.reseam.runtime.Export

@Export
@Synchronized
fun install(diagnostics: Boolean) {
    System.loadLibrary("MotionCamRecording")
    if (!installNative(diagnostics))
        error(
            "MotionCam: installing the native recording-limit patch failed; enable diagnostics for details",
        )
}

private external fun installNative(diagnostics: Boolean): Boolean
