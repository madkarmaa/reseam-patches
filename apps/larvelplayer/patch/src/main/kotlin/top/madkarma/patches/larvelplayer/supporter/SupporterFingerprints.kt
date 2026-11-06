// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.larvelplayer.supporter

import app.reseam.patch.method

internal val loadAdRemoval =
    method("loadAdRemoval") {
        name("onCreate")
        strings("billing_prefs", "ads_removed")
    }

internal const val SUPPORT_DESCRIPTION =
    "100% free, all features unlocked. Your support keeps the project alive."

internal val supportIntroduction =
    method("supportIntroduction") {
        strings(SUPPORT_DESCRIPTION)
    }
