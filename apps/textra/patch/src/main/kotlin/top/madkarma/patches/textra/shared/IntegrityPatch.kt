// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.textra.shared

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import java.util.Base64
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller

val textraIntegrity = patch {
    hidden()
    compatibleWith("com.textra")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))
    dependsOn(spoofApkFileAccess)

    execute {
        val packageName = manifest.packageName ?: error("Textra: missing package name")
        val certificate = files.signers().firstOrNull() ?: error("Textra: missing original signer")
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

        loadPairipLibrary.after { SignatureKiller.redirectRawApkOpens(string("libpairipcore.so")) }
        log.info(
            "Integrity: ${loadPairipLibrary.descriptor} enables original APK reads for the VM.",
        )
    }
}
