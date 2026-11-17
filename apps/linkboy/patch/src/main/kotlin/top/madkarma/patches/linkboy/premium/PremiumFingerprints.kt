// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.linkboy.premium

import app.reseam.patch.Type
import app.reseam.patch.classTarget
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.enumConstant
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

private val purchaseStates =
    klass("purchaseStates") {
        enumConstants("PURCHASED", "NOT_PURCHASED", "PENDING")
    }

internal val initializePurchaseStates = purchaseStates.method("<clinit>")

internal val purchasedState = purchaseStates.enumConstant("PURCHASED")

// The Store publishes these until Play Billing reports an owned "premium" product.
internal val lockedStates =
    listOf("UNKNOWN", "NOT_PURCHASED", "PENDING").map(purchaseStates::enumConstant)

private val stringArrayValue =
    klass("stringArrayValue") {
        strings("Array(items=")
    }

// Compose Resources' suspend loader is the only static reader of a decoded string array.
private val loadStringArray =
    method("loadStringArray") {
        accesses { owner(stringArrayValue.descriptor) }
        flags(AccessFlags.STATIC)
    }

// The loader takes the StringArrayResource first; nothing else names that class.
private val stringArrayResourceType =
    classTarget("stringArrayResourceType") {
        bytecode.findClass(loadStringArray.parameterTypes.first())
            ?: error("StringArrayResource class missing")
    }

internal val stringArrayResource =
    method("stringArrayResource") {
        hasParam(stringArrayResourceType.descriptor)
        returns(Type.List)
    }

private val composeResource =
    classTarget("composeResource") {
        stringArrayResourceType.classDef.superclass?.let(bytecode::findClass)
            ?: error("Compose resource base class missing")
    }

// Every Compose resource is keyed by "type:name", the only String field of their base class.
internal val resourceKey = composeResource.fieldOfType(Type.String)
