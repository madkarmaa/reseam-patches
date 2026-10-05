// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.signature

import app.reseam.patch.appEntry
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
        val originalSignature = signers.firstOrNull()
            ?: error("SignatureKiller: no v2/v3 signers found; unsigned or v1-only APKs are not supported")
        val encodedSignature = Base64.getEncoder().encodeToString(originalSignature)

        // Keep the before/after diagnostics and spoof in one ordered entry hook.
        appEntry {
            call(
                SignatureKiller.checkSignatures,
                application,
                string(packageName),
                string(encodedSignature)
            )
            call(
                SignatureKiller.killSignature, string(packageName), string(encodedSignature)
            )
            call(
                SignatureKiller.checkSignatures,
                application,
                string(packageName),
                string(encodedSignature)
            )
        }

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

        appEntry {
            call(SignatureKiller.killApkPath, application, string(packageName))
        }

        log.info("SignatureKiller: hooked $packageName with APK-path spoofing (${originalApk.size} byte origin, ${signers.size} signer(s)).")
    }
}

private val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

private object SignatureResources
