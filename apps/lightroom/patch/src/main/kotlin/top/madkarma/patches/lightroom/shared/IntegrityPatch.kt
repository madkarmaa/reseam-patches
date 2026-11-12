// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.lightroom.shared

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import java.util.Base64
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller

// Lightroom keeps the Pairip VM running, and the VM must read the original APK files to pass its
// integrity check.
val lightroomIntegrity = patch {
    hidden()
    compatibleWith("com.adobe.lrmobile")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))
    dependsOn(spoofApkFileAccess)

    execute {
        val packageName = manifest.packageName ?: error("Lightroom: missing package name")
        val certificate =
            files.signers().firstOrNull() ?: error("Lightroom: missing original signer")
        val signature = Base64.getEncoder().encodeToString(certificate)

        pairipBootstrap.before {
            SignatureKiller.initializeNativeFileAccess(
                string(packageName),
                string(signature),
                bool(false),
            )
        }
        log.info(
            "Integrity: ${pairipBootstrap.descriptor} prepares the original APK before startup.",
        )

        // Pairip's watchdog child traces its own syscalls and must not inherit the redirect.
        loadPairipLibrary.after {
            SignatureKiller.redirectRawApkOpensAfterFork(string("libpairipcore.so"))
        }
        log.info(
            "Integrity: ${loadPairipLibrary.descriptor} enables original APK reads for the VM.",
        )
    }
}
