// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.oto.premium

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.universal.signature.signatureKiller

val unlockPlus = patch("Unlock Plus") {
    description("Unlocks Plus-only features.")
    compatibleWith("com.piyush.music")
    // Startup kills the process unless the signing certificate is the developer's.
    dependsOn(signatureKiller)

    execute {
        storedPlusEntitlement.alwaysReturn(true)
        log.info("Plus: ${storedPlusEntitlement.descriptor} returns an active Plus entitlement.")

        isPlus.alwaysReturn(true)
        log.info("Plus: ${isPlus.descriptor} reports Plus as active.")

        // Re-signing breaks the container tag that the native library reads from the installed APK.
        // spoofApkFileAccess would serve the original APK instead, but it embeds a full copy and about
        // doubles the output size. Patching this one check costs nothing.
        validContainerTag.alwaysReturn(true)
        log.info("Plus: ${validContainerTag.descriptor} accepts the re-signed container tag.")

        initialStoredPlus.captureAs("storedPlus", Type.Boolean)
            .after { capture("storedPlus").assign(bool(true)) }
        log.info("Plus: ${initialStoredPlus.method.descriptor} starts the Plus state as active.")

        // The Plus state starts active, so the listener must not reload it from the stored preference.
        plusStateListener.alwaysReturn()
        log.info("Plus: ${plusStateListener.descriptor} no longer reloads the Plus state.")

        // The About screen's closing line is literal layout text, not a string resource.
        val creditLayouts = files.xmlFilesWith("android:text").filter { path ->
            files.xml(path).use { it.findByAttribute("android:text", ABOUT_CREDIT).isNotEmpty() }
        }
        val creditLayout = creditLayouts.singleOrNull()
            ?: error("Expected one layout with \"$ABOUT_CREDIT\", got ${creditLayouts.size}")

        files.editXml(creditLayout) {
            findByAttribute("android:text", ABOUT_CREDIT).single()["android:text"] =
                PATCH_ATTRIBUTION
        }
        log.info("Plus: $creditLayout shows the patch attribution on the About screen.")
    }
}

private const val ABOUT_CREDIT = "Crafted with care."
