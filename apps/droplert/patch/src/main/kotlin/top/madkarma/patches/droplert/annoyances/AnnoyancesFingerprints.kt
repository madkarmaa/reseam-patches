// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.droplert.annoyances

import app.reseam.patch.method

internal val requestReview =
    method("requestReview") {
        strings("requestInAppReview (%s)")
    }
