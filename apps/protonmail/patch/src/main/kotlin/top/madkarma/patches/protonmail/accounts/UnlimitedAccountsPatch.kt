// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.protonmail.accounts

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.protonmail.ProtonmailPatchStrings
import top.madkarma.patches.universal.nativelibs.removeUnusedNativeLibs
import top.madkarma.protonmail.accounts.bindings.Accounts

val unlimitedAccounts =
    patch(ProtonmailPatchStrings.unlimitedAccountsName) {
        description(ProtonmailPatchStrings.unlimitedAccountsDescription)
        compatibleWith("ch.protonmail.android")
        dependsOn(removeUnusedNativeLibs)

        val diagnostics =
            boolOption(
                "diagnostics",
                title = ProtonmailPatchStrings.unlimitedAccountsDiagnosticsTitle,
                description = ProtonmailPatchStrings.unlimitedAccountsDiagnosticsDescription,
                default = false,
            )

        execute {
            // Split installs keep each ABI's libraries in its own split, so the extension joins the
            // component that holds the app's Rust library.
            val architectures =
                files.components().flatMap { component ->
                    val componentFiles = files.component(component)
                    val paths = componentFiles.list().toSet()

                    listOf("arm64-v8a", "armeabi-v7a", "x86_64")
                        .filter { "lib/$it/libmail_uniffi.so" in paths }
                        .map { componentFiles to it }
                }

            if (architectures.isEmpty())
                error("Proton Mail: requires an ARM64, ARMv7, or x86_64 Rust library")

            for ((componentFiles, architecture) in architectures) {
                val path = "lib/$architecture/libProtonAccounts.so"
                val library =
                    NativeResources.javaClass.classLoader.getResourceAsStream(path)
                        ?: error("Proton Mail: bundled native extension missing: $path")
                library.use { componentFiles.writeStored(path, it) }
            }

            rustLibraryLoad.after {
                call(Accounts.install, bool(options[diagnostics]))
            }
            log.info(
                "Proton Mail: ${rustLibraryLoad.descriptor} installs the account-limit extension for ${architectures.joinToString { it.second }} after loading Rust.",
            )
        }
    }

private object NativeResources
