// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mapy.premium

import app.reseam.patch.*
import app.reseam.patch.dex.ref

private val premiumFeatures = klass("premiumFeatures") {
    strings("PremiumFeatures(userBadge=")
}

internal val premiumFeaturesConstructor = premiumFeatures.method("<init>") {
    hasParam(Type.Boolean)
}

internal val limitedOfflineMaps = klass("limitedOfflineMaps") {
    strings("Limited(count=")
}

private val unlimitedOfflineMaps = klass("unlimitedOfflineMaps") {
    strings("Unlimited")
    implements(limitedOfflineMaps.classDef.interfaces.single())
}

internal val limitedFeatureAccess = klass("limitedFeatureAccess") {
    strings("Limited(limit=")
}

// This interface gate also handles the restricted fallback used before account data is available.
internal val premiumFeatureGate = method("premiumFeatureGate") {
    inClass(klass(limitedFeatureAccess.classDef.interfaces.single()))
    returns(Type.Boolean)
}

private val unlimitedFeatureAccess = klass("unlimitedFeatureAccess") {
    strings("Unlimited")
    implements(limitedFeatureAccess.classDef.interfaces.single())
}

internal val unlimitedOfflineMapsInstance = fieldTarget("unlimitedOfflineMapsInstance") {
    unlimitedOfflineMaps.classDef.staticFields.singleOrNull()?.ref
        ?: error("Expected exactly one unlimited offline maps singleton")
}

internal val unlimitedFeatureAccessInstance = unlimitedFeatureAccess.field("INSTANCE")

private val customSpeedModel = klass("customSpeedModel") {
    strings("SettingsCustomSpeedSectionValueModel(titleResolver=")
}

internal val customSpeedConstructor = customSpeedModel.method("<init>")

// Reuse the app's StateFlow factory so collectors and the editor see the same true value.
internal val customSpeedStateFlow = method("customSpeedStateFlow") {
    calledBy(customSpeedConstructor)
    params(Type.Object)
}

// The constructor wraps its mutable flow in the same read-only type it accepts for Premium.
internal val customSpeedReadOnlyFlow = method("customSpeedReadOnlyFlow") {
    calledBy(customSpeedConstructor)
    custom { returnType in customSpeedConstructor.parameterTypes }
}

private val composeStringValue = klass("composeStringValue") {
    strings("Value(text=")
}

internal val composeStringValueConstructor = composeStringValue.method("<init>")
