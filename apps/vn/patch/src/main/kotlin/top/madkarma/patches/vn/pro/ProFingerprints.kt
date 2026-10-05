// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.vn.pro

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal val proGate = method("proGate") {
    strings("FORCE_PRO")
    inClass(klass("premiumManager") { strings("PremiumManage@") })
}

private val startupConfiguration = klass("com.frontrow.common.model.SplashAdvertisement")

internal val showStore = startupConfiguration.field("ShowStore")
internal val advertisementList = startupConfiguration.field("ADJSON")
internal val googleAdsEnabled = startupConfiguration.field("GoogleADEnabled")

internal val cachedStartupConfiguration = method("cachedStartupConfiguration") {
    inClass(klass("startupRepository") { strings("launch_app") })
    returns(startupConfiguration.descriptor)
}

internal val applyStartupConfiguration = method("applyStartupConfiguration") {
    strings("KEY_LAST_SHOW_PRO_FEATURE", "KEY_STICKER_SURVEY_URL")
}

internal val premiumManagerConstructor = klass(proGate.owner).method("<init>")

private val exportAdSectionConfig = klass("exportAdSectionConfig") {
    strings("ExportAdSectionConfig(showAds=")
}

internal val exportAdSectionConstructor = exportAdSectionConfig.method("<init>")

private val startupSurveyDecision = klass("startupSurveyDecision") {
    strings("StartupSurveyDecision(shouldShow=")
}

internal val startupSurveyDecisionConstructor = startupSurveyDecision.method("<init>")

internal val personalizationActivity =
    klass("com.frontrow.vlog.ui.onboarding.OnboardingSurveyActivity")

internal val subscriptionActivity = klass("com.frontrow.vlog.ui.premium.PremiumActivity")
