// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.ads

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val adManager = klass("adManager") {
    strings("featureDisableGamInitForPremium", "adsFeatureHouseAdsTimeout")
}

internal val shouldShowAds = method("shouldShowAds") {
    inClass(adManager)
    returns(Type.Boolean)
    params()
}
