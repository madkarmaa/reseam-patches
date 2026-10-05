// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.accuweather.premium

import app.reseam.patch.Type
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

private val userEntitlement = klass("userEntitlement") {
    strings("FAMILY_PREMIUM_PLUS", "SMB")
}

private val userEntitlementValueOf = userEntitlement.method("valueOf")

internal val reconcileSubscriptions = method("reconcileSubscriptions") {
    strings("Reconciliation requested: conclusive=")
    calls(userEntitlementValueOf)
}

// The reconciliation coroutine stores its override in its only String field.
internal val subscriptionOverride = klass(reconcileSubscriptions.owner).fieldOfType(Type.String)

internal val weatherPageFinished = method("weatherPageFinished") {
    strings("Clearing WebView History in onPageFinished()")
}
