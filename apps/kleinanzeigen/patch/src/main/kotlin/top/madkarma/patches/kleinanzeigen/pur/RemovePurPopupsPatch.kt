// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kleinanzeigen.pur

import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.kleinanzeigen.pur.bindings.ConsentBanner

val removePurPopups = patch("Remove Pur popups") {
    description("Skips the consent screen that advertises Kleinanzeigen Pur, without giving tracking consent.")
    compatibleWith("com.ebay.kleinanzeigen")

    execute {
        // Same result as the app's own path when consent is already settled.
        shouldShowConsentBanner.before {
            thisObject.call(hideConsentBanner)
            returnValue(ConsentBanner.notNeeded())
        }
        log.info("Pur: ${shouldShowConsentBanner.descriptor} never shows the consent banner.")
    }
}
