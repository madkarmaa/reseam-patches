// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kleinanzeigen.pur

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val adFreeSubscriptionToggle = klass("adFreeSubscriptionToggle") {
    strings("android_ad_free_subscription_enabled")
}

private val gdprHelper = klass("gdprHelper") {
    strings("GDPR: Cannot restore - TC string is empty")
}

// The consent check is the only consent method that reads the Pur feature toggle.
internal val shouldShowConsentBanner = method("shouldShowConsentBanner") {
    inClass(gdprHelper)
    accesses { owner(adFreeSubscriptionToggle.descriptor) }
}

// When consent is already settled, the check marks the banner as gone through this method.
internal val hideConsentBanner = method("hideConsentBanner") {
    inClass(gdprHelper)
    calledBy(shouldShowConsentBanner)
    params()
    returns(Type.Void)
}
