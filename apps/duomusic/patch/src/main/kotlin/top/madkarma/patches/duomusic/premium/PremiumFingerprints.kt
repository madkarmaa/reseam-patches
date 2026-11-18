// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.duomusic.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

// Ads, ad-gated features, and the Pro badge all read is_pro_user through this getter; the other
// methods naming the key are billing writers that return void.
internal val isProUser =
    method("isProUser") {
        strings("is_pro_user")
        returns(Type.Boolean)
    }

private val billingManager =
    klass("billingManager") {
        strings("BillingClient is null. Call initialize() first.")
    }

// Billing results pass the purchase state to the open screens, which toggle the Pro badge with it.
internal val notifyProStatus =
    method("notifyProStatus") {
        inClass(billingManager)
        params(Type.Boolean)
    }
