// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.accuweather.premium

import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.accuweather.MembershipAttribution
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockFamilyPremium = patch("Unlock Family Premium+") {
    description("Unlocks Premium-only features.")
    compatibleWith("com.accuweather.android")

    execute {
        // Use the app's override branch so every collector receives the same family entitlement.
        reconcileSubscriptions.before {
            thisObject.set(subscriptionOverride, string("FAMILY_PREMIUM_PLUS"))
        }
        log.info("Family Premium+: enabled via ${reconcileSubscriptions.descriptor}.")

        // The membership card is rendered by the online app, after the native page loads.
        weatherPageFinished.before {
            call(
                MembershipAttribution.install,
                paramOfType("android.webkit.WebView"),
                string(PATCH_ATTRIBUTION)
            )
        }
    }
}
