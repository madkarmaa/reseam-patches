// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.ibispaint.prime

import app.reseam.patch.Type
import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

// The native library reads every purchase state through this JNI adapter, so its name is kept.
private val purchaseManagerAdapter = klass("jp.ne.ibis.ibispaintx.app.jni.PurchaseManagerAdapter")

private val isPrimeMember = purchaseManagerAdapter.method("isPrimeMember")

private val isPurchased = purchaseManagerAdapter.method("isPurchased") { params() }

private val paymentItems =
    klass("paymentItems") {
        enumConstants("PRIME_MONTHLY", "PRIME_YEARLY")
    }

internal val primeYearly = paymentItems.enumConstant("PRIME_YEARLY")

// The Prime check asks the purchase manager whether it owns each Prime plan.
internal val ownsPaymentItem =
    method("ownsPaymentItem") {
        calledBy(isPrimeMember)
        params(paymentItems.descriptor)
    }

// Ads and watermarks are removed when the purchase manager owns any item.
internal val ownsAnyPaymentItem =
    method("ownsAnyPaymentItem") {
        calledBy(isPurchased)
        returns(Type.Boolean)
    }
