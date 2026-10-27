// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.poweramp.fullversion

import app.reseam.patch.*
import top.madkarma.extensions.poweramp.bindings.FullVersion
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.patches.universal.signature.spoofApkFileAccess

val unlockFullVersion = patch("Unlock full version") {
    description("Unlocks the full version features.")
    compatibleWith("com.maxmpz.audioplayer")
    // The native library verifies the installed APK's certificate itself.
    dependsOn(spoofApkFileAccess)

    execute {
        initializeLicensePreferences.after {
            FullVersion.initialize(staticField(fullVersionState), staticField(pendingLicenseExpiry))
            fullLicense()
        }
        initializeNativeLicense.after { fullLicense() }
        setIntegerPreference.before {
            param(0).assign(FullVersion.licenseInteger(thisObject, param(0)))
        }
        log.info("Full version: ${fullVersionState.owner}->${fullVersionState.name} starts with a full license and ${pendingLicenseExpiry.owner}->${pendingLicenseExpiry.name} with no pending expiry.")
        log.info("Full version: ${setIntegerPreference.descriptor} keeps the full license when cached state updates.")

        // Blank both purchase labels and show the attribution in place of their separator.
        val blankLabel =
            resources.addString("madkarma_blank", "") ?: error("Cannot add a blank label")

        for (label in listOf("one_time_purchase", "lifetime_license")) {
            val id = resources.id("string", label) ?: error("Purchase label $label is missing")
            val count = updatePurchaseCard.replaceAllLiterals(id.toLong(), blankLabel.toLong())
            check(count == 1) { "Expected one $label label on the purchase card" }
        }

        val separators = updatePurchaseCard.replaceAllStrings(" | ", PATCH_ATTRIBUTION)
        check(separators == 1) { "Expected one purchase terms separator" }
        log.info("Full version: ${updatePurchaseCard.descriptor} shows the shared patch attribution in place of the purchase terms.")

        check(
            resources.setStringEverywhere(
                "thanks_for_purchase", PATCH_ATTRIBUTION
            )
        ) { "Thank-you string is missing" }
        log.info("Full version: every thanks_for_purchase translation shows the shared patch attribution.")

        // The bridge also handles activity launches and settings encryption. Keep those
        // operations and JNI registration, replacing only the license-status response.
        val redirects = mapOf(
            "native_acquire" to FullVersion.acquire,
            "native_close" to FullVersion.close,
            "native_lock" to FullVersion.label,
            "native_release" to FullVersion.release,
        )
        for ((name, replacement) in redirects) {
            val count = bytecode.redirectCalls(licenseBridge.descriptor, name, replacement)
            check(count > 0) { "No calls to license bridge $name" }
            log.info("Full version: redirected $count calls to ${licenseBridge.descriptor}->$name.")
        }
    }
}

private fun CodeScope.fullLicense() {
    staticField(fullVersionState).call(setIntegerPreference, int(229))
    staticField(pendingLicenseExpiry).call(setIntegerPreference, int(0))
}
