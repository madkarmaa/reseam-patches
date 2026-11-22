// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.homeworkout.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

// The billing library's local store of in-app and subscription purchases.
private val purchaseStore =
    klass("purchaseStore") {
        strings("Migrating old purchaseData to split storage")
    }

// The store's only String-to-Boolean lookup reports whether a product is missing from both purchase
// lists. The Premium and ad-free checks derive their state from it for every product they accept.
internal val productNotPurchased =
    method("productNotPurchased") {
        inClass(purchaseStore)
        params(Type.String)
        returns(Type.Boolean)
    }
