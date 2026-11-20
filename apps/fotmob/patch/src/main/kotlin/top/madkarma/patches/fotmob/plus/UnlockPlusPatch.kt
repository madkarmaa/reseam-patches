// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.fotmob.plus

import app.reseam.patch.*
import top.madkarma.fotmob.plus.bindings.Entitlements
import top.madkarma.patches.fotmob.FotmobPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPlus =
    patch(FotmobPatchStrings.unlockFotmobPlusName) {
        description(FotmobPatchStrings.unlockFotmobPlusDescription)
        compatibleWith("com.mobilefootie.wc2010")

        execute {
            hasValidSubscription.alwaysReturn(true)
            log.info("FotMob+: ${hasValidSubscription.descriptor} reports a valid subscription.")

            activeEntitlements.replace {
                returnValue(Entitlements.activeEntitlements(lifetimeEntitlement()))
            }
            log.info(
                "FotMob+: ${activeEntitlements.descriptor} returns a lifetime FotMob+ entitlement.",
            )

            // The membership screen describes a lifetime entitlement with this text.
            if (!resources.setStringEverywhere("legacy_gold_member", PATCH_ATTRIBUTION))
                error("FotMob+: legacy_gold_member string missing")
            log.info("FotMob+: legacy_gold_member shows the shared patch attribution.")
        }
    }

private fun CodeScope.lifetimeEntitlement(): ValueRef =
    newInstance(
        entitlement.descriptor,
        entitlementConstructor.proto,
        string("FotMob+"), // Identifier.
        string(PATCH_ATTRIBUTION), // Localized name.
        bool(false), // Will renew.
        nullObject, // Expiration date.
        newInstance("Ljava/util/Date;"), // Original purchase date.
        bool(true), // Active.
        bool(false), // Sandbox.
        bool(false), // Trial.
        bool(false), // Subscription: a lifetime entitlement has nothing to manage or renew.
        bool(false), // Manageable on this platform.
        bool(false), // Manageable at all.
    )
