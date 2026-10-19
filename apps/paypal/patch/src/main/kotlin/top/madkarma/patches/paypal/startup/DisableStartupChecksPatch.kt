// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.paypal.startup

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.universal.signature.signatureKiller

val disableStartupChecks = patch("Disable startup checks") {
    description("Prevents local integrity and emulator checks from crashing the app at startup.")
    compatibleWith("com.paypal.android.p2pmobile")
    // app still reads the signing certificate after the startup verifier has been disabled.
    dependsOn(signatureKiller)

    execute {
        // Uncomment to require native APK spoofing from the signature-check dependency.
        // if ("assets/SignatureKiller/origin.apk" !in files.list()) error("PayPal requires Signature killer spoofApkPath=true")

        startupVerifier.alwaysReturn()
        log.info("Disabled startup verifier ${startupVerifier.descriptor}")
    }
}
