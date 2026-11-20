// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.timberradio.premium

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

// The setter writes the same preference key but returns nothing.
internal val isPremium =
    method("isPremium") {
        strings("premium_user")
        returns(Type.Boolean)
    }

// Handles the Play subscription query and logs this when no subscription is found.
// The Settings screen keeps its class and view binding names, and its Premium card always offers a
// subscription.
private val mainSettings = klass("com.msoft.timberradio.fragments.settings.MainSettingsFragment")

internal val mainSettingsViewCreated = mainSettings.method("onViewCreated")

internal val mainSettingsBinding =
    klass("com.msoft.timberradio.databinding.FragmentMainSettingsBinding")

internal val premiumCard = mainSettingsBinding.field("buyProContainer")
internal val subscribeButton = mainSettingsBinding.field("buyPremium")

private val purchasesResult =
    method("purchasesResult") {
        strings("queryPurchases:empty")
    }

// Turns the Premium-only settings back off when no subscription is found. Besides constructors, it
// is the only no-argument method the result handler calls in its own class.
internal val restoreFreeUserState =
    method("restoreFreeUserState") {
        calledBy(purchasesResult)
        inClass(klass(purchasesResult.owner))
        returns(Type.Void)
        params()
    }
