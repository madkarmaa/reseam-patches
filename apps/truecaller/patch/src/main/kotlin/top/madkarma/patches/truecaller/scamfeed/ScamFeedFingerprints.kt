// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.scamfeed

import app.reseam.patch.Type
import app.reseam.patch.method

internal val scamFeedEnabled = method("scamFeedEnabled") {
    strings("featureScamFeed")
    returns(Type.Boolean)
}
