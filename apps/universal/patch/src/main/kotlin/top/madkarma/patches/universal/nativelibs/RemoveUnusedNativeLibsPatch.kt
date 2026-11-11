// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.nativelibs

import app.reseam.patch.patch
import top.madkarma.patches.universal.UniversalPatchStrings

val removeUnusedNativeLibs =
    patch(UniversalPatchStrings.removeUnusedNativeLibsName) {
        description(
            UniversalPatchStrings.removeUnusedNativeLibsDescription,
        )

        val architecture =
            stringOption(
                "architecture",
                title = UniversalPatchStrings.removeUnusedNativeLibsArchitectureTitle,
                description = UniversalPatchStrings.removeUnusedNativeLibsArchitectureDescription,
                default = "arm64-v8a",
                validValues = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
                required = true,
            )

        execute {
            val keptArchitecture = options[architecture]

            // Split APKs cannot be dropped through the patch API, so split APKs for other
            // architectures are emptied of their libraries instead.
            val librariesByComponent =
                files.components().associateWith { component ->
                    files.component(component).list().filter { path ->
                        libraryArchitecture(path) != null
                    }
                }
            val shippedArchitectures =
                librariesByComponent.values.flatten().mapNotNull(::libraryArchitecture).toSet()

            if (shippedArchitectures.isEmpty()) {
                log.info("Native libraries: app ships no native libraries, nothing to do.")
                return@execute
            }
            if (keptArchitecture !in shippedArchitectures)
                error(
                    "Native libraries: app ships no $keptArchitecture libraries (found ${
                shippedArchitectures.sorted().joinToString()
            })",
                )

            for ((component, libraries) in librariesByComponent) {
                val removed = libraries.filter { path ->
                    libraryArchitecture(path) != keptArchitecture
                }
                if (removed.isEmpty()) continue

                removed.forEach(files.component(component)::delete)
                val architectures =
                    removed.mapNotNull(::libraryArchitecture).toSortedSet().joinToString()
                log.info(
                    "Native libraries: removed ${removed.size} $architectures files from $component, kept $keptArchitecture.",
                )
            }
        }
    }

/** The ABI directory of a `lib/<abi>/<file>` entry, or null for any other path. */
private fun libraryArchitecture(path: String): String? {
    val segments = path.split('/')
    if (segments.size < 3 || segments[0] != "lib") return null
    return segments[1]
}
