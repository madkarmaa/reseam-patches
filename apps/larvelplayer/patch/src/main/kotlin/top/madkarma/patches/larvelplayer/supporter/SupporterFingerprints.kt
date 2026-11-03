// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.larvelplayer.supporter

import app.reseam.patch.method

internal val loadAdRemoval = method("loadAdRemoval") {
    name("onCreate")
    strings("billing_prefs", "ads_removed")
}
