// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.shared.clearurls

import app.reseam.patch.*
import app.reseam.patch.types.MethodRef
import top.madkarma.patches.shared.hasPermission
import top.madkarma.shared.clearurls.bindings.ClearUrls

private const val INTENT = "android.content.Intent"
private const val INTERNET_PERMISSION = "android.permission.INTERNET"

/**
 * Patches that use the helpers below depend on this one. Their code blocks can also call the
 * `ClearUrls` binding directly, for example `ClearUrls.cleanIntent(intent)`. It stays hidden because
 * it removes nothing on its own.
 */
val clearUrls = patch("Load ClearURLs rules") {
    description(
        "Downloads the ClearURLs tracking rules when the app starts, and caches them for offline launches. Adds internet access to apps that lack it. Rules by the ClearURLs project: https://gitlab.com/ClearURLs/rules"
    )
    hidden()

    execute {
        // Apps with hardcoded links may never go online themselves, but the rules must be downloaded.
        // Only a missing permission changes the manifest, so mount builds keep this patch for apps that go online.
        if (!manifest.edit { hasPermission(INTERNET_PERMISSION) }) {
            manifest.addPermission(INTERNET_PERMISSION)
            log.info("ClearURLs: added $INTERNET_PERMISSION to download the rules.")
        }

        appEntry { ClearUrls.start(application) }
    }
}

/**
 * Cleans the links in every intent the app passes to `Intent.createChooser`, the call that opens the
 * share sheet. Returns how many calls it redirected.
 */
fun PatchRuntime.cleanSharedLinks(): Int {
    val redirectedCalls =
        redirectChooser(ClearUrls.createChooser) + redirectChooser(ClearUrls.createChooserWithCallback)
    if (redirectedCalls == 0) error("ClearURLs: the app never calls Intent.createChooser")
    return redirectedCalls
}

// createChooser is static and takes the intent first. redirectCalls(owner, name, to) would take that
// intent for the receiver of an instance call, so the method is named in full.
private fun PatchRuntime.redirectChooser(replacement: ExtMethod) = bytecode.redirectCalls(
    MethodRef(descriptor(INTENT), "createChooser", replacement.proto), replacement
)

/** The method's returned String, a link or text with links, has no tracking. */
fun MethodTarget.cleanReturnedLinks() = after { returnValue(ClearUrls.cleanText(result)) }

/** The method's String parameter, a link or text with links, has no tracking. */
fun MethodTarget.cleanLinksInParameter(index: Int) = before {
    param(index).assign(ClearUrls.cleanText(param(index)))
}
