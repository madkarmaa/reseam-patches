// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vn.pro

import app.reseam.patch.*
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere
import top.madkarma.vn.extensions.bindings.Pro

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("com.frontrow.vlog")

    execute {
        proGate.alwaysReturn(true)
        log.info("Pro: ${proGate.descriptor} enables Pro features.")

        // The extension uses these retained fields; fail here if the app model changes.
        check(
            showStore.type == descriptor("java.lang.Boolean") && advertisementList.type == Type.String && googleAdsEnabled.type == Type.Int
        ) { "Startup configuration fields have unsupported types" }

        // Initialize preferences before the manager reads them.
        premiumManagerConstructor.before {
            call(Pro.initializePreferences, paramOfType(Type.Context))
        }
        log.info("Pro: ${premiumManagerConstructor.descriptor} initializes premium preferences.")

        // Normalize cached configuration as well as each server response, before flags are applied or saved.
        cachedStartupConfiguration.after {
            call(Pro.normalizeStartupConfiguration, result)
        }
        log.info("Pro: ${cachedStartupConfiguration.descriptor} disables ads in cached startup configuration.")
        applyStartupConfiguration.before {
            call(Pro.normalizeStartupConfiguration, paramOfType(Type.Object))
        }
        log.info("Pro: ${applyStartupConfiguration.descriptor} disables ads in refreshed startup configuration.")

        // Both Boolean inputs control export banners: ads and guidance.
        val bannerFlags = exportAdSectionConstructor.parameterTypes.withIndex()
            .filter { it.value == Type.Boolean }

        check(bannerFlags.isNotEmpty()) { "Export banner flags are missing" }

        exportAdSectionConstructor.before {
            bannerFlags.forEach { (index, _) -> param(index).assign(bool(false)) }
        }
        log.info("Pro: ${exportAdSectionConstructor.descriptor} disables export banners.")

        startupSurveyDecisionConstructor.before {
            paramOfType(Type.Boolean).assign(bool(false))
        }
        log.info("Pro: ${startupSurveyDecisionConstructor.descriptor} skips the startup survey.")

        manifest.redirectActivity(
            className(personalizationActivity.descriptor),
            className(Pro.SkippedPersonalizationActivity.descriptor)
        )

        manifest.redirectActivity(
            className(subscriptionActivity.descriptor),
            className(Pro.SkippedSubscriptionActivity.descriptor)
        )

        check(resources.setStringEverywhere("frv_help_center", PATCH_ATTRIBUTION)) {
            "Help Center attribution string is missing"
        }
    }
}

// Preserve explicit intents to the original component while replacing its implementation.
private fun ManifestScope.redirectActivity(originalName: String, replacementName: String) {
    edit {
        val activity = findByTag("activity").singleOrNull { it["android:name"] == originalName }
            ?: error("Activity $originalName is missing or ambiguous")

        check(activity.children.isEmpty()) { "Activity $originalName has unexpected child elements" }

        activity["android:name"] = replacementName
    }

    addActivityAlias(replacementName, originalName)
}
