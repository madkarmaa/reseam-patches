// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.signature

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.isPresent
import top.madkarma.universal.signature.bindings.SignatureChecks
import top.madkarma.universal.signature.bindings.SignatureChecksApi33
import top.madkarma.universal.signature.bindings.SignatureKiller
import java.util.*

val signatureKiller = patch("Signature Killer") {
    description("Spoofs the original app signature and optionally redirects installed-file reads to the original app files.")

    val spoofApkPath = boolOption(
        "spoofApkPath",
        title = "Spoof APK file access",
        description = "Also embeds the original APK components and redirects reads of the installed APK files to them. Only enable this for apps that verify the APK files themselves; most apps merely ask for the signature. Leave off unless the app needs it.",
        default = false,
    )

    val diagnostics = boolOption(
        "diagnostics",
        title = "Log signature API diagnostics",
        description = "Log useful data under the SignatureKiller log tag.",
        default = false,
    )

    execute {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val signers = files.signers()
        if (signers.isEmpty()) error("SignatureKiller: no v2/v3 signers found; unsigned or v1-only APKs are not supported")

        // These APIs bypass PackageInfo.CREATOR. Redirect direct app calls; extension calls stay original.
        val manager = "android.content.pm.PackageManager"

        val certificateChecks = bytecode.redirectCalls(
            manager, "hasSigningCertificate", SignatureChecks.hasSigningCertificate,
        ) + bytecode.redirectCalls(
            manager, "hasSigningCertificate", SignatureChecks.hasSigningCertificateForUid,
        )

        val archiveReads = bytecode.redirectCalls(
            manager, "getPackageArchiveInfo", SignatureChecks.getPackageArchiveInfo,
        ) + bytecode.redirectCalls(
            manager, "getPackageArchiveInfo", SignatureChecksApi33.getPackageArchiveInfoWithFlags,
        )

        log.info("SignatureKiller: redirected $certificateChecks certificate check(s), $archiveReads archive read(s); reflection and JNI calls are not redirected.")

        if (!options[spoofApkPath]) {
            log.info("SignatureKiller: hooked $packageName (signature only, ${signers.size} signer(s)).")
            return@execute
        }

        var originalSize = 0L
        val assetPaths = mutableSetOf<String>()

        for (component in files.components()) {
            val splitName = manifest.component(component).splitName

            if (splitName != null && (splitName.isEmpty() || '/' in splitName || '\\' in splitName)) error(
                "SignatureKiller: invalid split name: $splitName"
            )

            val assetPath = if (splitName == null) "assets/SignatureKiller/origin.apk"
            else "assets/SignatureKiller/splits/$splitName.apk"

            if (!assetPaths.add(assetPath)) error("SignatureKiller: duplicate original component: $component")

            val original = files.component(component).sourceStream().use { it.readBytes() }
            files.writeStored(assetPath, original)
            originalSize += original.size
        }

        val nativeLibraries = files.components().flatMap { files.component(it).list() }
            .filter { it.startsWith("lib/") && it.endsWith(".so") }

        // Adding another ABI can make Android stop extracting the app's own native libraries.
        val nativeAbis = if (nativeLibraries.isEmpty()) NATIVE_ABIS else NATIVE_ABIS.filter { abi ->
            nativeLibraries.any { it.startsWith("lib/$abi/") }
        }

        if (nativeAbis.isEmpty()) error("SignatureKiller: no supported native ABI in the app")

        for (abi in nativeAbis) {
            val path = "lib/$abi/libSignatureKiller.so"
            val bytes = SignatureResources.javaClass.classLoader.getResourceAsStream(path)
                ?.use { it.readBytes() }
                ?: error("SignatureKiller: bundled native lib missing: $path")
            files.write(path, bytes)
        }

        log.info("SignatureKiller: hooked $packageName with APK-path spoofing (${assetPaths.size} original component(s), $originalSize bytes, ${signers.size} signer(s)).")
    }

    // Resolve the Application after dependent patches have finished editing the manifest.
    afterDependents {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val originalSignature =
            files.signers().firstOrNull() ?: error("SignatureKiller: no original signer")

        // Pairip signature checker can launch from a component class initializer, before attachBaseContext.
        if (isPresent(pairipStartup)) {
            pairipStartup.before {
                call(
                    SignatureKiller.initializeEarly,
                    string(packageName),
                    string(Base64.getEncoder().encodeToString(originalSignature)),
                    bool(options[spoofApkPath])
                )
            }

            log.info("SignatureKiller: early hook ${pairipStartup.descriptor}")

            // VMRunner loads its native library after the early startup hook has installed redirection.
            if (options[spoofApkPath] && isPresent(pairipInitialization)) pairipInitialization.after {
                call(SignatureKiller.refreshApkFileHooks)
            }
        }

        val attachContext = signatureAttachContext()
        attachContext.before {
            call(
                SignatureKiller.initialize,
                param(0),
                string(packageName),
                string(Base64.getEncoder().encodeToString(originalSignature)),
                bool(options[spoofApkPath])
            )

            if (options[diagnostics]) call(SignatureChecks.checkSignatureApis, param(0))
        }

        log.info("SignatureKiller: startup hook ${attachContext.descriptor}")

    }

}

private val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

private object SignatureResources
