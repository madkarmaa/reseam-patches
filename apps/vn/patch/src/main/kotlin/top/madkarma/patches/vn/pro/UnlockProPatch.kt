// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.vn.pro

import app.reseam.patch.*
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.vn.Pro

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("com.frontrow.vlog")

    execute {
        proGate.alwaysReturn(true)

        // The extension uses these retained fields; fail here if the app model changes.
        check(
            showStore.type == descriptor("java.lang.Boolean") && advertisementList.type == Type.String && googleAdsEnabled.type == Type.Int
        ) { "Startup configuration fields have unsupported types" }

        // Initialize preferences before the manager reads them.
        premiumManagerConstructor.before {
            call(Pro.initializePreferences, paramOfType(Type.Context))
        }

        // Normalize cached configuration as well as each server response, before flags are applied or saved.
        cachedStartupConfiguration.after {
            call(Pro.normalizeStartupConfiguration, capture("result"))
        }
        applyStartupConfiguration.before {
            call(Pro.normalizeStartupConfiguration, paramOfType(Type.Object))
        }

        // Both Boolean inputs control export banners: ads and guidance.
        val bannerFlags = exportAdSectionConstructor.parameterTypes.withIndex()
            .filter { it.value == Type.Boolean }

        check(bannerFlags.isNotEmpty()) { "Export banner flags are missing" }

        exportAdSectionConstructor.before {
            bannerFlags.forEach { (index, _) -> param(index).assign(bool(false)) }
        }

        startupSurveyDecisionConstructor.before {
            paramOfType(Type.Boolean).assign(bool(false))
        }

        manifest.redirectActivity(
            className(personalizationActivity.descriptor), Pro.PERSONALIZATION_ACTIVITY
        )

        manifest.redirectActivity(
            className(subscriptionActivity.descriptor), Pro.SUBSCRIPTION_ACTIVITY
        )

        check(resources.setString("frv_help_center", PATCH_ATTRIBUTION)) {
            "Help Center attribution string is missing"
        }

        log.info("Pro: ${proGate.descriptor} forced true.")
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
