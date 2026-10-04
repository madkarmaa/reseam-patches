// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.adguard.lifetime

import app.reseam.patch.dex.ref
import app.reseam.patch.field
import app.reseam.patch.fieldTarget
import app.reseam.patch.klass
import app.reseam.patch.method

internal val paidLicenseState = klass("paidLicenseState") {
    strings("PaidLicense(licenseKey=")
}

internal val paidLicenseConstructor = paidLicenseState.method("<init>")

private val lifetimeDuration = klass("lifetimeDuration") {
    strings("Lifetime", "lifetime")
}

internal val lifetimeInstance = fieldTarget("lifetimeInstance") {
    val lifetimeClass = lifetimeDuration.classDef
    lifetimeClass.staticFields.singleOrNull { it.fieldType == lifetimeClass.descriptor }?.ref
        ?: error("Expected exactly one Lifetime singleton field")
}

// Both license and subscription enums contain "Family"; only the license enum is a constructor input.
private val licenseTypes = klass("licenseTypes") {
    strings("Family")
    custom { descriptor in paidLicenseConstructor.parameterTypes }
}

internal val familyLicense = licenseTypes.field("Family")

internal val maskLicenseKey = method("maskLicenseKey") {
    inClass(klass("licenseScreen") { strings("\$licenseKeyLayout") })
    strings("*")
}

private val licenseKey = method("licenseKey") {
    strings(
        "null cannot be cast to non-null type com.adguard.android.management.plus.support.PlusState.PaidLicense",
    )
}

// The cache loader has no strings; it is the only method the license-key reader calls in its own class.
internal val initialCachedState = method("initialCachedState") {
    calledBy(licenseKey)
    inClass(klass(licenseKey.owner))
}

internal val fetchBackendState = method("fetchBackendState") {
    strings("The Backend response is null, let's provide the Unknown Plus state")
}

// Of the cache loader's callers, only the state updater accepts a PlusState.
internal val updateLicenseState = method("updateLicenseState") {
    calls(initialCachedState)
    params(initialCachedState.returnType)
}
