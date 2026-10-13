// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.ultra

import app.reseam.patch.*

private val subscriptionInfo = klass("subscriptionInfo") {
    strings("ActiveSubscriptionInfo(isUserSubscribed=")
}

// isUserSubscribed is the first constructor input; the other booleans have different roles.
internal val subscriptionConstructor = subscriptionInfo.method("<init>") {
    param(0, Type.Boolean)
}

private val planMetadata = klass("planMetadata") {
    strings("PlaneMeta(level=")
}

// Level is the first constructor input; the other integer is the storage limit.
internal val planConstructor = planMetadata.method("<init>") {
    param(0, Type.Int)
}

private val subscriptionTiers = klass("subscriptionTiers") {
    enumConstants("ULTRA", "MAX")
}

internal val tierType = planMetadata.fieldOfType(subscriptionTiers.descriptor)

internal val permissions = planMetadata.fieldOfType(Type.List)

internal val ultraTier = subscriptionTiers.enumConstant("ULTRA")
