// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.annoyances

import app.reseam.patch.*
import top.madkarma.patches.truecaller.shared.disableFeatureFlag
import top.madkarma.patches.truecaller.shared.featureFlagOverrides
import top.madkarma.truecaller.behavior.bindings.Decisions
import top.madkarma.truecaller.ui.bindings.Interface

val disableAnnoyances = patch("Disable annoyances") {
    description("Hides permission banners, surveys, update prompts, and automatic Premium offers.")
    compatibleWith("com.truecaller")
    dependsOn(featureFlagOverrides)

    execute {
        defaultDialerDeepLink.alwaysReturn()
        log.info("Annoyances: ${defaultDialerDeepLink.descriptor} ignores default-dialer links.")

        settingsPermissionBanner.before { paramOfType(Type.Boolean).assign(bool(false)) }
        log.info("Annoyances: ${settingsPermissionBanner.descriptor} hides permission and default-app banners in Settings.")

        for (target in listOf(notificationPermissionBanner, permissionPromosEnabled)) {
            target.replace { returnValue(Decisions.disabled()) }
            log.info("Annoyances: ${target.descriptor} disables permission banners.")
        }

        disableFeatureFlag("featureDefaultDialerInterstitial")
        disableFeatureFlag("featureDefaultDialerPermanentSpace")

        // Keep the ordinary blocking dialog and its request extras.
        disableFeatureFlag("featureBlockSurveyFlow")

        for (target in surveyActivities) {
            // Preserve the framework's required super.onCreate call before finishing.
            target.after { Interface.finishActivity(thisObject) }
            log.info("Annoyances: ${target.descriptor} closes surveys.")
        }

        currentUpdateType.replace { returnValue(Decisions.noUpdate()) }
        log.info("Annoyances: ${currentUpdateType.descriptor} returns no update.")

        inAppUpdateConfiguration.alwaysReturnNull()
        log.info("Annoyances: ${inAppUpdateConfiguration.descriptor} disables Google Play update prompts.")

        for (target in listOf(automaticUpgradeDecision, automaticUpgradeEligibility)) {
            target.replace { returnValue(Decisions.disabled()) }
            log.info("Annoyances: ${target.descriptor} blocks automatic Premium offers.")
        }
    }
}
