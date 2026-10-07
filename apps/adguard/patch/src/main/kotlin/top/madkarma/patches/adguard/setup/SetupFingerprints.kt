// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.adguard.setup

import app.reseam.patch.Type
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

internal const val PRIVACY_LEVEL = "com.adguard.android.management.filtering.StealthModeLevel"
internal const val FILTER_GROUP = "com.adguard.android.model.filter.FilterGroup"

internal val basePreferences = method("basePreferences") {
    strings("storageFactory", "base")
}

internal val setupPreferences = method("setupPreferences") {
    strings(
        "privacy_policy",
        "automatic_crash_reporting",
        "technical_and_interaction_data",
        "onboarding_first_shown",
        "onboarding_second_shown",
        "notifications_dialog_should_be_shown",
        "annoyances_enabled",
        "browsing_security_enabled",
        "stealth_mode_enabled",
        "annoyance_filter_ids_with_consent_approved"
    )
}

internal val filteringConstructor = method("filteringConstructor") {
    strings("Filtering manager is initialized")
}

internal val filteringContext = klass(filteringConstructor.owner).fieldOfType(Type.Context)

// Reads annoyance_filter_ids_with_consent_approved, with a default-set fallback.
internal val approvedAnnoyanceFilters = method("approvedAnnoyanceFilters") {
    inClass(klass(filteringConstructor.owner))
    params()
    returns("java.util.Set")
}

internal val filterGroupCode = klass(FILTER_GROUP).method("getCode")

private val onboardingConstructor = method("onboardingConstructor") {
    strings("onboarding-view-model")
}

private val applyConfiguration = method("applyConfiguration") {
    inClass(klass(onboardingConstructor.owner))
    calls { params(PRIVACY_LEVEL) }
}

internal val setPrivacyLevel = method("setPrivacyLevel") {
    calledBy(applyConfiguration)
    params(PRIVACY_LEVEL)
    returns(Type.Void)
}

private val deviceLanguages = method("deviceLanguages") {
    strings("getSystemLocales(...)")
}

internal val enableFilterGroup = method("enableFilterGroup") {
    inClass(klass(filteringConstructor.owner))
    params(FILTER_GROUP)
    returns(Type.Void)
    // Enabling a group selects recommended filters for the device's locales.
    calls(deviceLanguages)
}

internal val disableFilterGroup = method("disableFilterGroup") {
    inClass(klass(filteringConstructor.owner))
    params(FILTER_GROUP)
    returns(Type.Void)
    custom { name != enableFilterGroup.name }
}

private val setSearchAds = method("setSearchAds") {
    inClass(klass(filteringConstructor.owner))
    params(Type.Boolean)
    literals(10) // Search-ad exceptions filter.
}

private val updateFilters = method("updateFilters") {
    calledBy(setSearchAds)
    params(Type.List, Type.Boolean)
    returns(Type.Long)
}

internal val standardAdapter = method("standardAdapter") {
    calledBy(updateFilters)
    returns("com.adguard.flm.FlmAdapter")
}

internal val filterOperations = klass(filteringConstructor.owner).fieldOfType(updateFilters.owner)
internal val filterAdapters = klass(updateFilters.owner).fieldOfType(standardAdapter.owner)
