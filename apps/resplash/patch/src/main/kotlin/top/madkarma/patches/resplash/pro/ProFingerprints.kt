// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.resplash.pro

import app.reseam.patch.method

internal const val RESPLASH_PRO_QUERY = "SELECT * FROM resplash_pro LIMIT 1"

// Builds the Room LiveData that every Pro gate observes for the cached ResplashPro row.
internal val observeResplashPro =
    method("observeResplashPro") {
        strings(RESPLASH_PRO_QUERY)
    }
