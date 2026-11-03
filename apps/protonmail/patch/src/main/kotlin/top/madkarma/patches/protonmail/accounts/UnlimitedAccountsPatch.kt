// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.protonmail.accounts

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.universal.nativelibs.removeUnusedNativeLibs
import top.madkarma.protonmail.accounts.bindings.Accounts

val unlimitedAccounts = patch("Unlimited accounts") {
    description("Allows multiple free Proton Mail accounts.")
    compatibleWith("ch.protonmail.android")
    dependsOn(removeUnusedNativeLibs)

    execute {
        if ("lib/arm64-v8a/libmail_uniffi.so" !in files.list()) error("Proton Mail: requires the ARM64 Rust library; select arm64-v8a native libraries")

        val path = "lib/arm64-v8a/libProtonAccounts.so"
        val library = NativeResources.javaClass.classLoader.getResourceAsStream(path)
            ?: error("Proton Mail: bundled native extension missing: $path")
        library.use { files.writeStored(path, it) }

        rustLibraryLoad.after {
            call(Accounts.install)
        }
        log.info("Proton Mail: ${rustLibraryLoad.descriptor} installs the account-limit extension after loading Rust.")
    }
}

private object NativeResources
