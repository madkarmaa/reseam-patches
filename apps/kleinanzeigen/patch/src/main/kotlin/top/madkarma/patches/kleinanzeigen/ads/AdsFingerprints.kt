// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kleinanzeigen.ads

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.method

// The ad configuration parser fills the page-to-placement table that banners, native ads,
// and interstitials all look up their slot in.
internal val parseAdsConfiguration = method("parseAdsConfiguration") {
    accesses { type("java.util.EnumMap") }
    params(Type.Boolean)
}

internal val adPlacements =
    parseAdsConfiguration.field("adPlacements") { type("java.util.EnumMap") }
