// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.adguard

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object AdGuardSetup : ExtClass("top.madkarma.extensions.adguard.AdGuardSetup") {
    val create by static(Type.Context, "com.adguard.flm.FlmAdapter", returns = descriptor)

    val needsSetup by method(returns = Type.Boolean)

    val approvedAnnoyanceFilters by method(Type.Int, returns = "java.util.Set")

    val acceptTerms by method()
    val disableAutomaticCrashReporting by method()
    val disableUsageTelemetry by method()

    val markFirstOnboardingShown by method()
    val markSecondOnboardingShown by method()

    val setNotificationPromptEnabled by method(Type.Boolean)

    val setAnnoyanceBlockingEnabled by method(Type.Boolean)
    val setBrowsingSecurityEnabled by method(Type.Boolean)
    val setPrivacyProtectionEnabled by method(Type.Boolean)
    val setSearchAdsBlockingEnabled by method(Type.Boolean)
    val setSocialMediaFilterEnabled by method(Type.Boolean)
    val setCookieNoticesFilterEnabled by method(Type.Boolean)
    val setPopupsFilterEnabled by method(Type.Boolean)
    val setMobileAppBannersFilterEnabled by method(Type.Boolean)
    val setOtherAnnoyancesFilterEnabled by method(Type.Boolean)
    val setWidgetsFilterEnabled by method(Type.Boolean)

    val markSetupCompleted by method()
}
