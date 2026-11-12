// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.radarbot.shared

import app.reseam.patch.ExternalPatch
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import java.util.Base64
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller

private const val APP_CHECK_PARAMETER = "ts"

// The token the app sends while Firebase App Check has not issued one.
private const val APP_CHECK_PLACEHOLDER = "0"

// Radarbot runs its splash screen and receivers inside the Pairip VM, so the VM must keep running
// and read the original APK files to pass its integrity check.
val radarbotIntegrity = patch {
    hidden()
    compatibleWith("com.vialsoft.radarbot_free")
    dependsOn(ExternalPatch("reseam-patches", "app.reseam.patches.universal.removePairip"))
    dependsOn(spoofApkFileAccess)

    execute {
        val packageName = manifest.packageName ?: error("Radarbot: missing package name")
        val certificate =
            files.signers().firstOrNull() ?: error("Radarbot: missing original signer")
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

        // Firebase App Check rejects the re-signed app, and the servers answer requests carrying
        // the placeholder token with 777, which forces a login and withholds all data. Requests
        // without a token are accepted.
        val equals = "(Ljava/lang/Object;)Z"
        putRequestParameter.before {
            whenTrue(
                string(APP_CHECK_PARAMETER)
                    .callVirtual("java.lang.Object", "equals", equals, param(0)),
            ) {
                whenTrue(
                    string(APP_CHECK_PLACEHOLDER)
                        .callVirtual("java.lang.Object", "equals", equals, param(1)),
                ) {
                    returnNull()
                }
            }
        }
        log.info(
            "Integrity: ${putRequestParameter.descriptor} leaves out the App Check placeholder token.",
        )
    }
}
