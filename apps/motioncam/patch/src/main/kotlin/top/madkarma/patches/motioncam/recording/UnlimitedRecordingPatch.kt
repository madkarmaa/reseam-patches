// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.motioncam.recording

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.motioncam.recording.bindings.Recording

val unlimitedRecording =
    patch("Unlimited recording") {
        description("Removes the trial's five-second video recording limit.")
        compatibleWith("com.motioncam")
        dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))

        val diagnostics =
            boolOption(
                "diagnostics",
                title = "Log diagnostics",
                description =
                    "Log native recording-limit discovery and installation under the MotionCamRecording logcat tag.",
                default = false,
            )

        execute {
            val libraries =
                files.components().flatMap { component ->
                    files
                        .component(component)
                        .list()
                        .filter {
                            it.startsWith("lib/") && it.endsWith("/libnative-camera-host.so")
                        }
                        .map { component to it }
                }
            if (libraries.isEmpty())
                error("MotionCam: camera library missing; include the native APK split")

            for ((component, path) in libraries) {
                if (path != "lib/arm64-v8a/libnative-camera-host.so")
                    error("MotionCam: unsupported camera library architecture: $path")

                val extensionPath = "lib/arm64-v8a/libMotionCamRecording.so"
                val extension =
                    NativeResources.javaClass.classLoader.getResourceAsStream(extensionPath)
                        ?: error("MotionCam: bundled native extension missing: $extensionPath")

                extension.use { files.component(component).writeStored(extensionPath, it) }
            }

            cameraLibraryLoad.after {
                call(Recording.install, bool(options[diagnostics]))
            }
            log.info(
                "MotionCam: ${cameraLibraryLoad.descriptor} installs the recording-limit extension after loading the camera library.",
            )
        }
    }

private object NativeResources
