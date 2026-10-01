// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.pairip

import app.reseam.patch.patch

val removePairip = patch("Remove Pairip") {
    description(
        "Removes Pairip (Google Play automatic integrity protection). Optionally kills the Pairip VM. Does NOT bypass server-side Play Integrity attestation or pairipcore virtualization.",
    )

    val killVM = boolOption(
        "killVM",
        title = "Kill Pairip VM",
        description = "Also kills the Pairip VM. Only enable this for apps whose VM merely runs the startup integrity/license program. If the app routes real functionality through the VM enabling this breaks those features. Leave off unless the app needs it.",
        default = false,
    )

    val spoofInstaller = boolOption(
        "spoofInstaller",
        title = "Spoof installer checks",
        description = "Rewrites leftover installer-origin checks that reference the Play Store package so they report a Play Store installation.",
        default = true,
    )

    execute {
        var patched = 0

        manifest.edit {
            findByTag("application").firstOrNull()?.let { application ->
                if (application["android:name"] == PAIRIP_APPLICATION) {
                    bytecode.findClass(PAIRIP_APPLICATION)?.superclass?.removePrefix(
                        "L"
                    )?.removeSuffix(";")?.replace('/', '.')?.let { original ->
                        application["android:name"] = original
                    }
                }
            }

            findByAttribute(
                "android:name", LICENSE_ACTIVITY
            ).forEach { it.remove() }
            findByAttribute(
                "android:name", "com.android.vending.CHECK_LICENSE"
            ).forEach { it.remove() }

            for (tag in listOf(
                "activity", "activity-alias", "service", "receiver", "provider", "meta-data"
            )) {
                findByTag(tag).filter { it["android:name"]?.startsWith("com.pairip.") == true }
                    .forEach { it.remove() }
            }
        }

        bytecode.findClass(LICENSE_CLIENT)?.let { client ->
            patched += noOp(client, *LICENSE_CLIENT_VOID_METHODS)
            patched += returnTrue(
                client, "performLocalInstallerCheck", "isIsolated"
            )
            patched += forceLicenseResponseOk(client)
            patched += disableRepeatedCheck(client)
        }

        bytecode.findClass(LICENSE_CLIENT_V3)?.let { client ->
            patched += noOp(client, "onActivityCreate")
        }

        for (validator in listOf(
            "com.pairip.licensecheck.ResponseValidator",
            "com.pairip.licensecheck.LicenseResponseHelper"
        )) {
            bytecode.findClass(validator)?.let { classDef ->
                classDef.methods.filter { it.name == "validateResponse" }.forEach {
                    if (it.returnType == "V") it.alwaysReturn() else it.alwaysReturn(
                        true
                    )
                    patched++
                }
            }
        }

        bytecode.findClass(LICENSE_CONTENT_PROVIDER)?.let { provider ->
            patched += returnTrue(provider, "onCreate")
        }

        bytecode.findClass(LICENSE_ACTIVITY)?.let { activity ->
            patched += noOp(activity, *LICENSE_ACTIVITY_VOID_METHODS)
        }

        bytecode.findClass(SIGNATURE_CHECK)?.let { signatureCheck ->
            patched += noOp(signatureCheck, "verifyIntegrity")
            patched += returnTrue(signatureCheck, "verifySignatureMatches")
        }

        bytecode.findClass(PAIRIP_APPLICATION)?.let { wrapper ->
            wrapper.methods.filter { it.name == "attachBaseContext" }.forEach {
                it.remove()
                patched++
            }
        }

        for (classDef in bytecode.classes.filter {
            it.descriptor.startsWith(
                PAIRIP_DESCRIPTOR_PREFIX
            )
        }) {
            classDef.methods.filter { it.name == "openPlayStore" }.forEach {
                it.alwaysReturn()
                patched++
            }
        }

        if (options[killVM]) {
            bytecode.findClass(VM_RUNNER)?.let { vmRunner ->
                patched += noOp(vmRunner, "<clinit>")

                vmRunner.methods.filter { it.name == "invoke" }.forEach {
                    it.alwaysReturnNull()
                    patched++
                }

                patched += returnTrue(vmRunner, "isDebuggingEnabled")
            }

            bytecode.findClass(STARTUP_LAUNCHER)?.let { startupLauncher ->
                patched += noOp(startupLauncher, "launch", "pairip")
            }
        }

        if (options[spoofInstaller]) {
            patched += spoofInstallerChecks(bytecode)
        }

        if (patched == 0) {
            log.warn("Pairip: no Pairip methods found; no bytecode changes applied.")
        } else {
            log.info("Pairip: $patched method(s) neutralized.")
        }
    }
}
