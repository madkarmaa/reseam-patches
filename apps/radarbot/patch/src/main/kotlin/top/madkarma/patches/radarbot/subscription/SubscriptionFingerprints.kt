// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.radarbot.subscription

import app.reseam.patch.*

// Rebuilds the enabled feature set from the free tier, every active tier, and gamification rewards.
internal val rebuildFeatures =
    method("rebuildFeatures") {
        strings("free_access")
        calls { name("getActiveFeaturesFromRewards") }
    }

// Entitlement names ("pro_access", "gold_access", "truck_access") and their active flags share
// indexes, ordered from lowest to highest tier.
internal val tierNames = rebuildFeatures.field { type("[Ljava/lang/String;") }
internal val tierFlags = rebuildFeatures.field { type("[Z") }

internal val initializeTiers = klass(rebuildFeatures.owner).method("<clinit>")

private val userProperties =
    method("userProperties") {
        strings("x_user")
    }

// The user properties report the "verified" flag next to the excluded-user check, and both live in
// the same security holder.
private val userVerified = userProperties.field { type("Ljava/lang/Boolean;") }

// The stored "x_user" flag disables every feature and blocks the subscription check.
internal val isExcludedUser =
    method("isExcludedUser") {
        calledBy(userProperties)
        inClass(klass(userVerified.owner))
    }

// After a 777 server response, the radar database callback caches the excluded-user flag and
// replaces the subscription and feature checks with ones that always refuse.
private val databaseUrlCallback =
    method("databaseUrlCallback") {
        literals(777)
        accesses {
            owner(isExcludedUser.owner)
            type("Ljava/lang/Boolean;")
        }
    }

// The exclusion is gated on the server's "check_x_user" preference. Its key is encrypted and the
// callback is one case of a merged lambda, so the guard has no declaration of its own; its single
// preference read is the smallest unique anchor.
internal val excludeUserCheck =
    databaseUrlCallback
        .points("excludeUserCheck") {
            invokeInterface { name("getBoolean") }
            then { resultOf(Type.Boolean) }
        }
        .single()
        .captureAs("excludeUser")
