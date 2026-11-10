// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.adguard.setup

import app.reseam.patch.*
import top.madkarma.extensions.adguard.bindings.AdGuardSetup
import top.madkarma.patches.adguard.AdguardPatchStrings
import top.madkarma.patches.shared.isPresent

val skipSetup =
    patch(AdguardPatchStrings.skipSetupName) {
        description(
            AdguardPatchStrings.skipSetupDescription,
        )
        compatibleWith("com.adguard.android")

        val blockSearchAds =
            boolOption(
                "blockSearchAds",
                title = AdguardPatchStrings.skipSetupBlockSearchAdsTitle,
                description = AdguardPatchStrings.skipSetupBlockSearchAdsDescription,
                default = true,
            )

        val blockSocialWidgets =
            boolOption(
                "blockSocialWidgets",
                title = AdguardPatchStrings.skipSetupBlockSocialWidgetsTitle,
                description = AdguardPatchStrings.skipSetupBlockSocialWidgetsDescription,
                default = true,
            )

        val blockAnnoyances =
            boolOption(
                "blockAnnoyances",
                title = AdguardPatchStrings.skipSetupBlockAnnoyancesTitle,
                description = AdguardPatchStrings.skipSetupBlockAnnoyancesDescription,
                default = true,
            )

        val privacyLevel =
            stringOption(
                "privacyLevel",
                title = AdguardPatchStrings.skipSetupPrivacyLevelTitle,
                description = AdguardPatchStrings.skipSetupPrivacyLevelDescription,
                default = "Standard",
                validValues = listOf("None", "Standard", "High"),
            )

        val blockDangerousSites =
            boolOption(
                "blockDangerousSites",
                title = AdguardPatchStrings.skipSetupBlockDangerousSitesTitle,
                description = AdguardPatchStrings.skipSetupBlockDangerousSitesDescription,
                default = true,
            )

        val hideNotificationPrompt =
            boolOption(
                "hideNotificationPrompt",
                title = AdguardPatchStrings.skipSetupHideNotificationPromptTitle,
                description = AdguardPatchStrings.skipSetupHideNotificationPromptDescription,
                default = true,
            )

        val skipApprovalPopups =
            boolOption(
                "skipApprovalPopups",
                title = AdguardPatchStrings.skipSetupSkipApprovalPopupsTitle,
                description = AdguardPatchStrings.skipSetupSkipApprovalPopupsDescription,
                default = true,
            )

        execute {
            if (basePreferences.all.isEmpty() || !isPresent(setupPreferences))
                error("Skip setup: unsupported preference storage")
            log.info(
                "Setup: ${basePreferences.all.joinToString { it.descriptor }} open the base preferences that hold the setup flags.",
            )

            val selectedPrivacyLevel = options[privacyLevel]
            val privacyEnabled = selectedPrivacyLevel != "None"
            val securityEnabled = options[blockDangerousSites]

            if (options[skipApprovalPopups]) {
                approvedAnnoyanceFilters.replace {
                    val adapter =
                        thisObject
                            .field(filterOperations)
                            .field(filterAdapters)
                            .call(standardAdapter)

                    val setup =
                        call(AdGuardSetup.create, thisObject.field(filteringContext), adapter)

                    returnValue(
                        setup.call(
                            AdGuardSetup.approvedAnnoyanceFilters,
                            staticField(annoyancesGroup).call(filterGroupCode),
                        ),
                    )
                }

                log.info(
                    "Setup: ${approvedAnnoyanceFilters.descriptor} treats annoyance filters as approved.",
                )
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
                        bool(!options[hideNotificationPrompt]),
                    )
                    log.info(
                        "Setup: notification prompt enabled=${!options[hideNotificationPrompt]}.",
                    )

                    setup.call(
                        AdGuardSetup.setAnnoyanceBlockingEnabled,
                        bool(options[blockSocialWidgets] || options[blockAnnoyances]),
                    )
                    log.info(
                        "Setup: annoyance blocking enabled=${options[blockSocialWidgets] || options[blockAnnoyances]}.",
                    )

                    setup.call(AdGuardSetup.setBrowsingSecurityEnabled, bool(securityEnabled))
                    log.info("Setup: browsing security enabled=$securityEnabled.")

                    setup.call(AdGuardSetup.setPrivacyProtectionEnabled, bool(privacyEnabled))
                    log.info("Setup: privacy protection enabled=$privacyEnabled.")

                    thisObject.call(
                        if (privacyEnabled) enableFilterGroup else disableFilterGroup,
                        staticField(privacyGroup),
                    )
                    log.info("Setup: privacy filter group enabled=$privacyEnabled.")

                    if (privacyEnabled) {
                        thisObject.call(
                            setPrivacyLevel,
                            staticField(privacyLevels.enumConstant(selectedPrivacyLevel)),
                        )
                        log.info("Setup: privacy level set to $selectedPrivacyLevel.")
                    }

                    thisObject.call(
                        if (securityEnabled) enableFilterGroup else disableFilterGroup,
                        staticField(securityGroup),
                    )
                    log.info("Setup: security filter group enabled=$securityEnabled.")

                    setup.call(
                        AdGuardSetup.setSearchAdsBlockingEnabled,
                        bool(options[blockSearchAds]),
                    )
                    log.info("Setup: search ads blocking enabled=${options[blockSearchAds]}.")

                    setup.call(
                        AdGuardSetup.setSocialMediaFilterEnabled,
                        bool(options[blockSocialWidgets]),
                    )
                    log.info("Setup: Social Media filter enabled=${options[blockSocialWidgets]}.")

                    setup.call(
                        AdGuardSetup.setCookieNoticesFilterEnabled,
                        bool(options[blockAnnoyances]),
                    )
                    log.info("Setup: Cookie Notices filter enabled=${options[blockAnnoyances]}.")

                    setup.call(AdGuardSetup.setPopupsFilterEnabled, bool(options[blockAnnoyances]))
                    log.info("Setup: Popups filter enabled=${options[blockAnnoyances]}.")

                    setup.call(
                        AdGuardSetup.setMobileAppBannersFilterEnabled,
                        bool(options[blockAnnoyances]),
                    )
                    log.info(
                        "Setup: Mobile App Banners filter enabled=${options[blockAnnoyances]}.",
                    )

                    setup.call(
                        AdGuardSetup.setOtherAnnoyancesFilterEnabled,
                        bool(options[blockAnnoyances]),
                    )
                    log.info("Setup: Other Annoyances filter enabled=${options[blockAnnoyances]}.")

                    setup.call(AdGuardSetup.setWidgetsFilterEnabled, bool(options[blockAnnoyances]))
                    log.info("Setup: Widgets filter enabled=${options[blockAnnoyances]}.")

                    setup.call(AdGuardSetup.markSetupCompleted)
                    log.info("Setup: completion marked to preserve later manual changes.")
                }
            }
            log.info(
                "Setup: ${filteringConstructor.descriptor} applies the selected configuration on first startup.",
            )
        }
    }
