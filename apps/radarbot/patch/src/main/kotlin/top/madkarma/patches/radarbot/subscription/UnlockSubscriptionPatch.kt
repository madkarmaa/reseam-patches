// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.radarbot.subscription

import app.reseam.patch.*
import top.madkarma.extensions.radarbot.bindings.Subscription
import top.madkarma.patches.radarbot.RadarbotPatchStrings
import top.madkarma.patches.radarbot.shared.radarbotIntegrity
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

private const val RADARBOT = "com.vialsoft.radarbot_free"

// The status of an active subscription on the subscription screen ("Valid").
private const val SUBSCRIPTION_STATUS = "state_current"

val unlockGold =
    patch(RadarbotPatchStrings.unlockGoldName) {
        description(RadarbotPatchStrings.unlockGoldDescription)
        compatibleWith(RADARBOT)
        dependsOn(radarbotIntegrity)

        execute {
            if (!resources.setStringEverywhere(SUBSCRIPTION_STATUS, PATCH_ATTRIBUTION))
                error("Gold: missing subscription status string $SUBSCRIPTION_STATUS")
            log.info("Gold: string/$SUBSCRIPTION_STATUS displays the shared patch attribution.")

            grantTier("gold_access")
            log.info("Gold: ${initializeTiers.descriptor} activates the Gold and Pro tiers.")

            val flagWrites = tierFlagWrites.all
            if (flagWrites.isEmpty()) error("Gold: the subscription refresh writes no tier flags")

            flagWrites.forEach { it.redirectTo(Subscription.writeTierFlag) }
            log.info(
                "Gold: ${flagWrites.first().method.descriptor} keeps granted tiers active in its ${flagWrites.size} tier flag writes.",
            )

            isExcludedUser.alwaysReturn(false)
            log.info("Gold: ${isExcludedUser.descriptor} ignores the stored excluded-user flag.")

            excludeUserCheck.after { capture("excludeUser").assign(bool(false)) }
            log.info(
                "Gold: ${excludeUserCheck.method.descriptor} no longer excludes the user after a 777 response.",
            )
        }
    }

val unlockTruck =
    patch(RadarbotPatchStrings.unlockTruckName) {
        description(RadarbotPatchStrings.unlockTruckDescription)
        compatibleWith(RADARBOT)
        enabledByDefault(false)
        dependsOn(unlockGold)

        execute {
            grantTier("truck_access")
            log.info("Truck: ${initializeTiers.descriptor} activates the Truck tier.")
        }
    }

// The tier flags start inactive, so grant the tier once at class initialization; the redirected
// refresh writes keep it active afterwards.
private fun grantTier(tier: String) = initializeTiers.after {
    call(Subscription.grant, staticField(tierNames), staticField(tierFlags), string(tier))
}
