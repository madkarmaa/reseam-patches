// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kleinanzeigen.share

import app.reseam.patch.method

// Profile share links carry their campaign parameters in one constant.
internal const val PROFILE_SHARE_TRACKING =
    "&utm_source=sharesheet&utm_campaign=socialbuttons&utm_medium=social_profil&utm_content=app_android"

// Ad and seller share links get their campaign parameters from this helper.
internal val appendShareTracking = method("appendShareTracking") {
    strings("&utm_medium=social&utm_campaign=socialbuttons&utm_content=app_android")
}
