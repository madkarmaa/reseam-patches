@file:Suppress("unused")

package top.madkarma.patches.universal

import app.reseam.patch.*
import top.madkarma.revisions.recordedPatch
import java.util.*

object SignatureKiller : ExtClass("bin.mt.signature.SignatureKiller") {
    val killSignature = static("killSignature", Type.String, Type.String)
    val killApkPath = static("killApkPath", Type.Context, Type.String)
    val checkSignatures = static("checkSignatures", Type.Context, Type.String, Type.String)
}

private val NATIVE_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

private val patchClassLoader = object {}.javaClass.classLoader

val bypassSignatureChecks = recordedPatch("Bypass signature checks") {
    description(
        "Spoofs the original app signature using ApkSignatureKillerEx.",
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
        if (signers.isEmpty()) {
            error("SignatureKiller: no v2/v3 signers found; unsigned or v1-only APKs are not supported")
        }
        val base64Sig = Base64.getEncoder().encodeToString(signers[0])

        // One block so the diagnostic always runs before the spoof it measures
        // (killApkPath never touches PackageManager state, so its separate
        // block cannot disturb the reading whatever the emission order is).
        appEntry.before {
            call(
                SignatureKiller.checkSignatures, thisObject, string(packageName), string(base64Sig)
            )
            call(
                SignatureKiller.killSignature, string(packageName), string(base64Sig)
            )
        }

        if (options[spoofApkPath]) {
            val originalApk =
                files.source() ?: error("SignatureKiller: original APK bytes unavailable")
            files.writeStored("assets/SignatureKiller/origin.apk", originalApk)

            for (abi in NATIVE_ABIS) {
                val path = "lib/$abi/libSignatureKiller.so"
                val bytes = patchClassLoader.getResourceAsStream(path)?.use { it.readBytes() }
                    ?: error("SignatureKiller: bundled native lib missing: $path")
                files.write("lib/$abi/libSignatureKiller.so", bytes)
            }

            appEntry.before {
                call(
                    SignatureKiller.killApkPath, thisObject, string(packageName)
                )
            }

            log.info("SignatureKiller: hooked $packageName with APK-path spoofing (${originalApk.size} byte origin, ${signers.size} signer(s)).")
        } else {
            log.info("SignatureKiller: hooked $packageName (signature only, ${signers.size} signer(s)).")
        }

    }
}
