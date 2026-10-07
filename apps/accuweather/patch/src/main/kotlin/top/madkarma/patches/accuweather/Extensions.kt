// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.accuweather

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object MembershipAttribution :
    ExtClass("top.madkarma.extensions.accuweather.MembershipAttribution") {

    val install by static("android.webkit.WebView", Type.String)
}
