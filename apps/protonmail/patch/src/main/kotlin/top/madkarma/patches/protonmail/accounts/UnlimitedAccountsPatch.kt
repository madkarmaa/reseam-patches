// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.protonmail.accounts

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.universal.nativelibs.removeUnusedNativeLibs
import top.madkarma.protonmail.accounts.bindings.Accounts

val unlimitedAccounts =
    patch("Unlimited accounts") {
        description("Allows multiple free Proton Mail accounts.")
        compatibleWith("ch.protonmail.android")
        dependsOn(removeUnusedNativeLibs)

        val diagnostics =
            boolOption(
                "diagnostics",
                title = "Log diagnostics",
                description =
                    "Log native account-limit discovery and installation under the ProtonAccounts logcat tag.",
                default = false,
            )

        execute {
            val paths = files.list().toSet()
            val architectures =
                listOf(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                    )
                    .filter { "lib/$it/libmail_uniffi.so" in paths }
            if (architectures.isEmpty())
                error("Proton Mail: requires an ARM64, ARMv7, or x86_64 Rust library")

            for (architecture in architectures) {
                val path = "lib/$architecture/libProtonAccounts.so"
                val library =
                    NativeResources.javaClass.classLoader.getResourceAsStream(path)
                        ?: error("Proton Mail: bundled native extension missing: $path")
                library.use { files.writeStored(path, it) }
            }

            rustLibraryLoad.after {
                call(Accounts.install, bool(options[diagnostics]))
            }
            log.info(
                "Proton Mail: ${rustLibraryLoad.descriptor} installs the account-limit extension for ${architectures.joinToString()} after loading Rust.",
            )
        }
    }

private object NativeResources
