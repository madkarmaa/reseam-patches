// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.integrity

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller

val bypassNativeApkChecks = patch {
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(spoofApkFileAccess)

    execute {
        metaSecLibraryLoad.after {
            call(SignatureKiller.redirectRawApkOpens, string("libmetasec_ov.so"))
        }
        log.info("Integrity: ${metaSecLibraryLoad.descriptor} installs a process-wide seccomp redirect for MetaSec raw openat syscalls after loading the library.")
    }
}
