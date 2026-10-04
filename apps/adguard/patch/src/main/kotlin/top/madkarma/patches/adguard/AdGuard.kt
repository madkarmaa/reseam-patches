// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.adguard

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object AdGuardSetup : ExtClass("top.madkarma.extensions.adguard.AdGuardSetup") {
    val create = static("create", Type.Context, "com.adguard.flm.FlmAdapter", returns = descriptor)

    val needsSetup = method("needsSetup", returns = Type.Boolean)

    val approvedAnnoyanceFilters =
        method("approvedAnnoyanceFilters", Type.Int, returns = "java.util.Set")

    val acceptTerms = method("acceptTerms")
    val disableAutomaticCrashReporting = method("disableAutomaticCrashReporting")
    val disableUsageTelemetry = method("disableUsageTelemetry")

    val markFirstOnboardingShown = method("markFirstOnboardingShown")
    val markSecondOnboardingShown = method("markSecondOnboardingShown")

    val setNotificationPromptEnabled = method("setNotificationPromptEnabled", Type.Boolean)

    val setAnnoyanceBlockingEnabled = method("setAnnoyanceBlockingEnabled", Type.Boolean)
    val setBrowsingSecurityEnabled = method("setBrowsingSecurityEnabled", Type.Boolean)
    val setPrivacyProtectionEnabled = method("setPrivacyProtectionEnabled", Type.Boolean)
    val setSearchAdsBlockingEnabled = method("setSearchAdsBlockingEnabled", Type.Boolean)
    val setSocialMediaFilterEnabled = method("setSocialMediaFilterEnabled", Type.Boolean)
    val setCookieNoticesFilterEnabled = method("setCookieNoticesFilterEnabled", Type.Boolean)
    val setPopupsFilterEnabled = method("setPopupsFilterEnabled", Type.Boolean)
    val setMobileAppBannersFilterEnabled = method("setMobileAppBannersFilterEnabled", Type.Boolean)
    val setOtherAnnoyancesFilterEnabled = method("setOtherAnnoyancesFilterEnabled", Type.Boolean)
    val setWidgetsFilterEnabled = method("setWidgetsFilterEnabled", Type.Boolean)

    val markSetupCompleted = method("markSetupCompleted")
}
