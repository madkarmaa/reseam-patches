// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mango.pro

import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.point

private val billingRepository = klass("billingRepository") {
    strings("billing_cached_is_pro")
}

private val billingModes = klass("billingModes") {
    enumConstants("YEARLY_CANCELED")
}

private val realMode = billingModes.enumConstant("REAL")
internal val lifetimeMode = billingModes.enumConstant("LIFETIME")
internal val initializeBilling = billingRepository.method("<init>")

internal val defaultBillingMode = initializeBilling.point {
    field {
        owner(realMode.owner)
        name(realMode.name)
    }
}.captureAs("billingMode")
