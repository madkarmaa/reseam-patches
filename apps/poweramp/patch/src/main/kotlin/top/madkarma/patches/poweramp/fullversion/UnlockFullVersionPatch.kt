// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.poweramp.fullversion

import app.reseam.patch.*
import top.madkarma.extensions.poweramp.bindings.FullVersion
import top.madkarma.extensions.poweramp.bindings.Playback
import top.madkarma.patches.poweramp.PowerampPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.universal.signature.spoofApkFileAccess

val unlockFullVersion =
    patch(PowerampPatchStrings.unlockFullVersionName) {
        description(PowerampPatchStrings.unlockFullVersionDescription)
        compatibleWith("com.maxmpz.audioplayer")
        // The native library verifies the installed APK's certificate itself.
        dependsOn(spoofApkFileAccess)

        execute {
            initializeLicensePreferences.after {
                FullVersion.initialize(
                    staticField(fullVersionState),
                    staticField(pendingLicenseExpiry),
                )
                fullLicense()
            }

            // The core library keeps its own trial clock and, once the trial ends, rejects every
            // playback pipeline built after the first one in a process. Ship the native fix beside
            // each copy of the core library and install it once the license VM is initialized.
            val coreLibraries =
                files.components().flatMap { component ->
                    files
                        .component(component)
                        .list()
                        .filter { it.startsWith("lib/") && it.endsWith("/libpowerampcore.so") }
                        .map { component to it }
                }
            if (coreLibraries.isEmpty())
                error("Poweramp: core library missing; include the native APK split")

            for ((component, coreLibrary) in coreLibraries) {
                val extensionPath =
                    coreLibrary.replace("libpowerampcore.so", "libPowerampPlayback.so")
                val extension =
                    NativeResources.javaClass.classLoader.getResourceAsStream(extensionPath)
                        ?: error("Poweramp: no native playback fix for $coreLibrary")
                extension.use { files.component(component).writeStored(extensionPath, it) }
            }

            initializeNativeLicense.after {
                fullLicense()
                call(Playback.allowPlayback)
            }
            log.info(
                "Full version: ${initializeNativeLicense.descriptor} makes the core library accept every playback pipeline license verdict (${coreLibraries.joinToString { it.second }}).",
            )
            setIntegerPreference.before {
                param(0).assign(FullVersion.licenseInteger(thisObject, param(0)))
            }
            log.info(
                "Full version: ${fullVersionState.owner}->${fullVersionState.name} starts with a full license and ${pendingLicenseExpiry.owner}->${pendingLicenseExpiry.name} with no pending expiry.",
            )
            log.info(
                "Full version: ${setIntegerPreference.descriptor} keeps the full license when cached state updates.",
            )

            // Blank both purchase labels and show the attribution in place of their separator.
            val blankLabel =
                resources.addString("madkarma_blank", "") ?: error("Cannot add a blank label")

            for (label in listOf("one_time_purchase", "lifetime_license")) {
                val id = resources.id("string", label) ?: error("Purchase label $label is missing")
                val count =
                    updatePurchaseCard.method.replaceAllLiterals(id.toLong(), blankLabel.toLong())
                check(count == 1) { "Expected one $label label on the purchase card" }
            }

            val separators = updatePurchaseCard.method.replaceAllStrings(" | ", PATCH_ATTRIBUTION)
            check(separators == 1) { "Expected one purchase terms separator" }
            log.info(
                "Full version: ${updatePurchaseCard.descriptor} shows the shared patch attribution in place of the purchase terms.",
            )

            check(
                resources.setStringEverywhere(
                    "thanks_for_purchase",
                    PATCH_ATTRIBUTION,
                ),
            ) {
                "Thank-you string is missing"
            }
            log.info(
                "Full version: every thanks_for_purchase translation shows the shared patch attribution.",
            )

            postMessage.before { whenTrue(FullVersion.dropLicensePost()) { returnVoid() } }
            log.info(
                "Full version: ${postMessage.descriptor} drops what the native library posts during license calls.",
            )

            // Find both tagged callers before the bridge calls they make are redirected below.
            launchActivity.replace {
                FullVersion.launchActivity(param(0), param(1))
                returnVoid()
            }
            log.info(
                "Full version: ${launchActivity.descriptor} starts activities without the native library.",
            )
            log.info(
                "Full version: ${readLicenseStatus.descriptor} reads the license status labels from the extension.",
            )

            // The bridge also handles settings encryption and the license checks other native code
            // relies on. Keep those and JNI registration, answering only the status read.
            val redirects =
                mapOf(
                    "native_acquire" to FullVersion.acquire,
                    "native_close" to FullVersion.close,
                    "native_lock" to FullVersion.label,
                    "native_release" to FullVersion.release,
                )
            for ((name, replacement) in redirects) {
                val count = bytecode.redirectCalls(licenseBridge.descriptor, name, replacement)
                check(count > 0) { "No calls to license bridge $name" }
                log.info(
                    "Full version: redirected $count calls to ${licenseBridge.descriptor}->$name.",
                )
            }
        }
    }

private fun CodeScope.fullLicense() {
    staticField(fullVersionState).call(setIntegerPreference, int(229))
    staticField(pendingLicenseExpiry).call(setIntegerPreference, int(0))
}

private object NativeResources
