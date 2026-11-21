// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mobiscanner.premium

import app.reseam.patch.klass
import app.reseam.patch.method

// Combines the developer flags, the stored premium account, Google Play purchases, the legacy
// license and the MobiSystems account entitlement. The ad gates and Premium UI read it. The
// app-open ad manager inlines only the developer flags, with parameters.
internal val paidStatus =
    method("paidStatus") {
        strings("devPaid", "isPaid")
        params()
    }

private val feature = klass("com.mobisystems.monetization.feature.Feature")

// Every Feature gate, including the OCR tier, goes through the static check that canRun forwards
// to; most callers call it directly.
internal val featureAccess =
    method("featureAccess") {
        calledBy(feature.method("canRun"))
    }
