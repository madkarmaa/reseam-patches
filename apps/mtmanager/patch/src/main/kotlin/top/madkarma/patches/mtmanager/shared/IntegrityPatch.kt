// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mtmanager.shared

import app.reseam.patch.*
import java.util.Base64
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller

val integrity = patch {
    compatibleWith("bin.mt.plus")
    dependsOn(spoofApkFileAccess)

    execute {
        val packageName = manifest.packageName ?: error("Application package is missing")
        val signature = files.signers().singleOrNull() ?: error("Expected one original signer")
        val encodedSignature = Base64.getEncoder().encodeToString(signature)

        packageCreatorConstructor.before {
            val creator = paramOfType($$"android.os.Parcelable$Creator")
            creator.assign(
                SignatureKiller.wrapPackageCreator(
                    creator,
                    string(packageName),
                    string(encodedSignature),
                ),
            )
        }
        log.info(
            "Integrity: ${packageCreatorConstructor.descriptor} retains an original-signature creator delegate.",
        )

        protectionStartup.before {
            SignatureKiller.initializeBeforeNativeLibrary(
                string(packageName),
                string(encodedSignature),
                string("libmtprotect.so"),
                bool(false),
            )
        }
        log.info(
            "Integrity: ${protectionStartup.descriptor} prepares original APK access before native registration.",
        )
    }
}
