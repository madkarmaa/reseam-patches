// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.quicksettingstile.premium

import app.reseam.patch.Type
import app.reseam.patch.enumConstant
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

// Unresolved, Free, or Premium: the state every Premium check reads.
private val entitlementState =
    klass("entitlementState") { enumConstants("Unresolved", "Free", "Premium") }

internal val premiumEntitlement = entitlementState.enumConstant("Premium")

// Holds the entitlement cached in the "premium_entitlement" DataStore key and the Play Billing
// result.
private val entitlementRepository =
    klass("entitlementRepository") { strings("premium_entitlement") }

internal val cachedEntitlement = entitlementRepository.fieldOfType(entitlementState.descriptor)

// Publishes the cached entitlement to the state flow; the DataStore load and every billing update
// end here.
internal val publishEntitlement =
    method("publishEntitlement") {
        inClass(entitlementRepository)
        params()
        returns(Type.Void)
        custom { name != "<init>" }
    }

// The only setter of the Premium flag from Play Billing, which also persists it to DataStore.
internal val updateEntitlement =
    method("updateEntitlement") {
        inClass(entitlementRepository)
        params(Type.Boolean, Type.Long)
    }
