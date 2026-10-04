// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.adguard.setup

import app.reseam.patch.*
import top.madkarma.patches.adguard.AdGuardSetup
import top.madkarma.patches.shared.isPresent

val skipSetup = patch("Skip setup") {
    description(
        "Configure the app before installing it. Does NOT include the advanced filters available in the app settings."
    )
    compatibleWith("com.adguard.android"("4.15.0"))

    val blockSearchAds = boolOption(
        "blockSearchAds",
        title = "Block ads in web search",
        description = "Remove ads from your search engine results.",
        default = true
    )

    val blockSocialWidgets = boolOption(
        "blockSocialWidgets",
        title = "Block social media widgets",
        description = "Get rid of Like, Share, and other social buttons on websites.",
        default = true
    )

    val blockAnnoyances = boolOption(
        "blockAnnoyances",
        title = "Block annoyances",
        description = "Remove cookie notifications, live chats, and newsletter signups.",
        default = true
    )

    val privacyLevel = stringOption(
        "privacyLevel",
        title = "Protect your privacy",
        description = "Protect yourself from online trackers and analytics systems.",
        default = "Standard",
        validValues = listOf("None", "Standard", "High")
    )

    val blockDangerousSites = boolOption(
        "blockDangerousSites",
        title = "Block dangerous websites",
        description = "Be alerted before you access phishing and malicious websites.",
        default = true
    )

    val hideNotificationPrompt = boolOption(
        "hideNotificationPrompt",
        title = "Hide \"Enable notifications\" prompt",
        description = "Disables the \"Enable notifications\" prompt shown post-setup.",
        default = true
    )

    val skipApprovalPopups = boolOption(
        "skipApprovalPopups",
        title = "Skip approval popups",
        description = "Treat all annoyance filters as approved to skip consent dialogs when enabling them.",
        default = true
    )

    execute {
        if (!isPresent(basePreferences) || !isPresent(setupPreferences)) error("Skip setup: unsupported preference storage")

        val selectedPrivacyLevel = options[privacyLevel]
        val privacyEnabled = selectedPrivacyLevel != "None"
        val securityEnabled = options[blockDangerousSites]

        if (options[skipApprovalPopups]) {
            approvedAnnoyanceFilters.replace {
                val adapter =
                    thisObject.field(filterOperations).field(filterAdapters).call(standardAdapter)

                val setup = call(AdGuardSetup.create, thisObject.field(filteringContext), adapter)

                returnValue(
                    setup.call(
                        AdGuardSetup.approvedAnnoyanceFilters,
                        enumValue(FILTER_GROUP, "Annoyances").call(filterGroupCode)
                    )
                )
            }

            log.info("Setup: ${approvedAnnoyanceFilters.descriptor} treats annoyance filters as approved.")
        }

        filteringConstructor.after {
            val adapter =
                thisObject.field(filterOperations).field(filterAdapters).call(standardAdapter)

            val setup = call(AdGuardSetup.create, paramOfType(Type.Context), adapter)

            whenTrue(setup.call(AdGuardSetup.needsSetup)) {
                setup.call(AdGuardSetup.acceptTerms)
                log.info("Setup: terms accepted.")

                setup.call(AdGuardSetup.disableAutomaticCrashReporting)
                log.info("Setup: automatic crash reporting disabled.")

                setup.call(AdGuardSetup.disableUsageTelemetry)
                log.info("Setup: usage telemetry disabled.")

                setup.call(AdGuardSetup.markFirstOnboardingShown)
                log.info("Setup: first onboarding marked as shown.")

                setup.call(AdGuardSetup.markSecondOnboardingShown)
                log.info("Setup: second onboarding marked as shown.")

                setup.call(
                    AdGuardSetup.setNotificationPromptEnabled,
                    bool(!options[hideNotificationPrompt])
                )
                log.info("Setup: notification prompt enabled=${!options[hideNotificationPrompt]}.")

                setup.call(
                    AdGuardSetup.setAnnoyanceBlockingEnabled,
                    bool(options[blockSocialWidgets] || options[blockAnnoyances])
                )
                log.info("Setup: annoyance blocking enabled=${options[blockSocialWidgets] || options[blockAnnoyances]}.")

                setup.call(AdGuardSetup.setBrowsingSecurityEnabled, bool(securityEnabled))
                log.info("Setup: browsing security enabled=$securityEnabled.")

                setup.call(AdGuardSetup.setPrivacyProtectionEnabled, bool(privacyEnabled))
                log.info("Setup: privacy protection enabled=$privacyEnabled.")

                thisObject.call(
                    if (privacyEnabled) enableFilterGroup else disableFilterGroup,
                    enumValue(FILTER_GROUP, "Privacy")
                )
                log.info("Setup: privacy filter group enabled=$privacyEnabled.")

                if (privacyEnabled) {
                    thisObject.call(
                        setPrivacyLevel, enumValue(PRIVACY_LEVEL, selectedPrivacyLevel)
                    )
                    log.info("Setup: privacy level set to $selectedPrivacyLevel.")
                }

                thisObject.call(
                    if (securityEnabled) enableFilterGroup else disableFilterGroup,
                    enumValue(FILTER_GROUP, "Security")
                )
                log.info("Setup: security filter group enabled=$securityEnabled.")

                setup.call(
                    AdGuardSetup.setSearchAdsBlockingEnabled, bool(options[blockSearchAds])
                )
                log.info("Setup: search ads blocking enabled=${options[blockSearchAds]}.")

                setup.call(
                    AdGuardSetup.setSocialMediaFilterEnabled, bool(options[blockSocialWidgets])
                )
                log.info("Setup: Social Media filter enabled=${options[blockSocialWidgets]}.")

                setup.call(
                    AdGuardSetup.setCookieNoticesFilterEnabled, bool(options[blockAnnoyances])
                )
                log.info("Setup: Cookie Notices filter enabled=${options[blockAnnoyances]}.")

                setup.call(AdGuardSetup.setPopupsFilterEnabled, bool(options[blockAnnoyances]))
                log.info("Setup: Popups filter enabled=${options[blockAnnoyances]}.")

                setup.call(
                    AdGuardSetup.setMobileAppBannersFilterEnabled, bool(options[blockAnnoyances])
                )
                log.info("Setup: Mobile App Banners filter enabled=${options[blockAnnoyances]}.")

                setup.call(
                    AdGuardSetup.setOtherAnnoyancesFilterEnabled, bool(options[blockAnnoyances])
                )
                log.info("Setup: Other Annoyances filter enabled=${options[blockAnnoyances]}.")

                setup.call(AdGuardSetup.setWidgetsFilterEnabled, bool(options[blockAnnoyances]))
                log.info("Setup: Widgets filter enabled=${options[blockAnnoyances]}.")

                setup.call(AdGuardSetup.markSetupCompleted)
                log.info("Setup: completion marked to preserve later manual changes.")
            }
        }
    }
}
