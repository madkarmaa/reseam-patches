// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.signature

import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.universal.SignatureKiller
import java.util.*

val bypassSignatureChecks = patch("Bypass signature checks") {
    description(
        "Spoofs the original app signature (port of ApkSignatureKillerEx).",
    )

    val spoofApkPath = boolOption(
        "spoofApkPath",
        title = "Spoof APK file access",
        description = "Also embeds the original APK and redirects reads of the installed APK file to it. Only enable this for apps that verify the APK file itself; most apps merely ask for the signature. Leave off unless the app needs it.",
        default = false,
    )

    execute {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val signers = files.signers()
        if (signers.isEmpty()) error("SignatureKiller: no v2/v3 signers found; unsigned or v1-only APKs are not supported")

        if (!options[spoofApkPath]) {
            log.info("SignatureKiller: hooked $packageName (signature only, ${signers.size} signer(s)).")
            return@execute
        }

        val originalApk = files.sourceStream().use { it.readBytes() }
        files.writeStored("assets/SignatureKiller/origin.apk", originalApk)

        for (abi in NATIVE_ABIS) {
            val path = "lib/$abi/libSignatureKiller.so"
            val bytes = SignatureResources.javaClass.classLoader.getResourceAsStream(path)
                ?.use { it.readBytes() }
                ?: error("SignatureKiller: bundled native lib missing: $path")
            files.write(path, bytes)
        }

        log.info("SignatureKiller: hooked $packageName with APK-path spoofing (${originalApk.size} byte origin, ${signers.size} signer(s)).")
    }

    // Resolve the Application after dependent patches have finished editing the manifest.
    afterDependents {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val originalSignature =
            files.signers().firstOrNull() ?: error("SignatureKiller: no original signer")

        val attachContext = signatureAttachContext()

        attachContext.before {
            call(
                SignatureKiller.initialize,
                param(0),
                string(packageName),
                string(Base64.getEncoder().encodeToString(originalSignature)),
                bool(options[spoofApkPath])
            )
        }

        log.info("SignatureKiller: startup hook ${attachContext.descriptor}")
    }

}

private val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

private object SignatureResources
