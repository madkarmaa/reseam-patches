// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.adguard.setup

import app.reseam.patch.*

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

internal val setPrivacyLevel = applyConfiguration.point {
    invokeInterface { params(PRIVACY_LEVEL) }
}.callee("setPrivacyLevel")

private val enableSecurityGroup = applyConfiguration.point {
    field { owner(FILTER_GROUP); name("Security") }
}.next {
    invokeInterface { params(FILTER_GROUP) }
}

internal val enableFilterGroup = enableSecurityGroup.callee("enableFilterGroup")

// The next group call is the opposite branch of the same dangerous-sites choice.
internal val disableFilterGroup = enableSecurityGroup.next {
    invokeInterface { params(FILTER_GROUP) }
}.callee("disableFilterGroup")

// The first setting written by the tuning flow is the inverse of "Block search ads".
private val allowSearchAds = applyConfiguration.point {
    invokeInterface { params(Type.Boolean) }
}.callee("allowSearchAds")

private val setSearchAds = method("setSearchAds") {
    inClass(klass(filteringConstructor.owner))
    name(allowSearchAds.name)
    params(Type.Boolean)
}

private val updateFilters = setSearchAds.point {
    invokeVirtual { params(Type.List, Type.Boolean); returns(Type.Long) }
}.callee("updateFilters")

internal val standardAdapter = updateFilters.point {
    invokeVirtual { returns("com.adguard.flm.FlmAdapter") }
}.callee("standardAdapter")

internal val filterOperations = klass(filteringConstructor.owner).fieldOfType(updateFilters.owner)
internal val filterAdapters = klass(updateFilters.owner).fieldOfType(standardAdapter.owner)
