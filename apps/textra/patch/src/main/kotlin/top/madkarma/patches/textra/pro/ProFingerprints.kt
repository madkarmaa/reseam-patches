// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.textra.pro

import app.reseam.patch.Type
import app.reseam.patch.method

private val checkPurchases =
    method("checkPurchases") {
        strings("%s: checkPurchases(): purchases=%s")
    }

private val isPaid =
    method("isPaid") {
        calledBy(checkPurchases)
        returns(Type.Boolean)
    }

internal val licenseState =
    method("licenseState") {
        calledBy(isPaid)
        returns("java.lang.Integer")
    }
