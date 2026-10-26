// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.signature

import app.reseam.patch.PatchRuntime
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.isPresent
import top.madkarma.universal.signature.bindings.SignatureChecks
import top.madkarma.universal.signature.bindings.SignatureChecksApi33
import top.madkarma.universal.signature.bindings.SignatureKiller
import java.security.MessageDigest
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
        title = "Log diagnostics",
        description = "Log detailed runtime diagnostics from the extension and its native library under the SignatureKiller logcat tag.",
        default = false,
    )

    execute {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val signers = files.signers()
        if (signers.isEmpty()) error("SignatureKiller: no v2/v3 signers found; unsigned or v1-only APKs are not supported")

        log.info(
            "SignatureKiller: $packageName has ${signers.size} original signer(s): ${
            signers.joinToString {
                certificateFingerprint(
                    it
                )
            }
        }; the first is spoofed. Options: spoofApkPath=${options[spoofApkPath]}, diagnostics=${options[diagnostics]}.")

        // These APIs bypass PackageInfo.CREATOR. Redirect direct app calls; extension calls stay original.
        val manager = "android.content.pm.PackageManager"

        val packageChecks = bytecode.redirectCalls(
            manager, "hasSigningCertificate", SignatureChecks.hasSigningCertificate,
        )
        val uidChecks = bytecode.redirectCalls(
            manager, "hasSigningCertificate", SignatureChecks.hasSigningCertificateForUid,
        )

        val archiveReads = bytecode.redirectCalls(
            manager, "getPackageArchiveInfo", SignatureChecks.getPackageArchiveInfo,
        )
        val flaggedArchiveReads = bytecode.redirectCalls(
            manager, "getPackageArchiveInfo", SignatureChecksApi33.getPackageArchiveInfoWithFlags,
        )

        log.info("SignatureKiller: redirected hasSigningCertificate calls by package ($packageChecks) and by uid ($uidChecks), getPackageArchiveInfo calls with int flags ($archiveReads) and PackageInfoFlags ($flaggedArchiveReads); reflection and JNI calls are not redirected.")

        if (!options[spoofApkPath]) {
            log.info("SignatureKiller: hooked $packageName (${signers.size} signer(s)).")
            return@execute
        }

        includeOriginalApkFiles()
    }

    // Resolve the Application after dependent patches have finished editing the manifest.
    afterDependents {
        val packageName =
            manifest.packageName ?: error("SignatureKiller: manifest has no package name")

        val originalSignature =
            files.signers().firstOrNull() ?: error("SignatureKiller: no original signer")

        // Either the option or a dependent patch embeds the original APK files.
        val spoofApkFiles = ORIGIN_ASSET_PATH in files.list()
        val encodedSignature = Base64.getEncoder().encodeToString(originalSignature)
        log.info(
            "SignatureKiller: runtime spoofs certificate ${
                certificateFingerprint(
                    originalSignature
                )
            }; original APK file access ${if (spoofApkFiles) "on (embedded by the option or a dependent patch)" else "off"}; runtime diagnostics ${if (options[diagnostics]) "on" else "off"}."
        )

        // Pairip signature checker can launch from a component class initializer, before attachBaseContext.
        if (isPresent(pairipStartup)) {
            pairipStartup.before {
                call(
                    SignatureKiller.initializeEarly,
                    string(packageName),
                    string(encodedSignature),
                    bool(spoofApkFiles),
                    bool(options[diagnostics]),
                )
            }

            log.info("SignatureKiller: early hook ${pairipStartup.descriptor} initializes before Pairip's startup checks.")
        } else {
            log.info("SignatureKiller: no Pairip startup launcher; initialization starts in attachBaseContext only.")
        }

        val attachContext = signatureAttachContext()
        attachContext.before {
            call(
                SignatureKiller.initialize,
                param(0),
                string(packageName),
                string(encodedSignature),
                bool(spoofApkFiles),
                bool(options[diagnostics]),
            )
        }
        log.info("SignatureKiller: startup hook ${attachContext.descriptor} (original APK file access ${if (spoofApkFiles) "on" else "off"}).")
    }
}

/** An explicit dependency for apps whose native checks read the installed APK files. */
val spoofApkFileAccess = patch {
    dependsOn(signatureKiller)

    // Signature Killer enables the runtime redirection once it finds the embedded original.
    execute {
        includeOriginalApkFiles()
    }
}

private fun PatchRuntime.includeOriginalApkFiles() {
    val packageName = manifest.packageName ?: error("SignatureKiller: manifest has no package name")
    // The option and a dependent patch may both request the same embedded original.
    if (ORIGIN_ASSET_PATH in files.list()) {
        log.info("SignatureKiller: original APK files are already embedded.")
        return
    }

    val signers = files.signers()
    var originalSize = 0L
    val assetPaths = mutableSetOf<String>()

    for (component in files.components()) {
        val splitName = manifest.component(component).splitName

        if (splitName != null && (splitName.isEmpty() || '/' in splitName || '\\' in splitName)) error(
            "SignatureKiller: invalid split name: $splitName"
        )

        val assetPath = if (splitName == null) ORIGIN_ASSET_PATH
        else "assets/SignatureKiller/splits/$splitName.apk"

        if (!assetPaths.add(assetPath)) error("SignatureKiller: duplicate original component: $component")

        val original = files.component(component).sourceStream().use { it.readBytes() }
        files.writeStored(assetPath, original)
        originalSize += original.size
        log.info("SignatureKiller: embeds original $component (${splitName?.let { "split $it" } ?: "base"}, ${original.size} bytes) as $assetPath.")
    }

    val nativeLibraries = files.components().flatMap { files.component(it).list() }
        .filter { it.startsWith("lib/") && it.endsWith(".so") }

    // Adding another ABI can make Android stop extracting the app's own native libraries.
    val nativeAbis = if (nativeLibraries.isEmpty()) NATIVE_ABIS else NATIVE_ABIS.filter { abi ->
        nativeLibraries.any { it.startsWith("lib/$abi/") }
    }

    if (nativeAbis.isEmpty()) error("SignatureKiller: no supported native ABI in the app")

    val appAbis = nativeLibraries.map { it.split('/')[1] }.distinct()
    log.info(
        "SignatureKiller: adds libSignatureKiller.so for ${nativeAbis.joinToString()} (app native ABIs: ${
            appAbis.ifEmpty {
                listOf(
                    "none"
                )
            }.joinToString()
        })."
    )

    for (abi in nativeAbis) {
        val path = "lib/$abi/libSignatureKiller.so"
        val bytes = SignatureResources.javaClass.classLoader.getResourceAsStream(path)
            ?.use { it.readBytes() } ?: error("SignatureKiller: bundled native lib missing: $path")
        files.write(path, bytes)
    }

    log.info("SignatureKiller: hooked $packageName with APK-path spoofing (${assetPaths.size} original component(s), $originalSize bytes, ${signers.size} signer(s)).")
}

/** The first 16 hex digits of a certificate's SHA-256, matching the extension's log. */
internal fun certificateFingerprint(certificate: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
        .take(16)

private const val ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk"

private val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

private object SignatureResources
