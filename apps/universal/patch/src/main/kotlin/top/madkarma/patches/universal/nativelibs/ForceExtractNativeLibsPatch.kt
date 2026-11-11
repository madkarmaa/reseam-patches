// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.nativelibs

import app.reseam.patch.patch
import top.madkarma.patches.universal.UniversalPatchStrings

val forceExtractNativeLibs =
    patch(UniversalPatchStrings.forceExtractNativeLibsName) {
        description(UniversalPatchStrings.forceExtractNativeLibsDescription)

        execute {
            var flipped = false
            manifest.edit {
                val application =
                    findByTag("application").firstOrNull()
                        ?: error("ExtractNativeLibs: manifest has no <application> element")

                if (application["android:extractNativeLibs"]?.lowercase() == "false") {
                    application["android:extractNativeLibs"] = "true"
                    flipped = true
                }
            }

            if (flipped) log.info("ExtractNativeLibs: extractNativeLibs flipped to true.")
            else log.info("ExtractNativeLibs: extractNativeLibs already true, nothing to do.")
        }
    }
