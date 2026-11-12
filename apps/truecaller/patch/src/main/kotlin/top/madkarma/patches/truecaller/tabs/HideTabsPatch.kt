// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.tabs

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import app.reseam.patch.settings.gate
import app.reseam.patch.settings.section
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.settings.truecallerSettings
import top.madkarma.truecaller.behavior.bindings.Decisions
import top.madkarma.truecaller.settings.TruecallerSettings
import top.madkarma.truecaller.settings.TruecallerStrings
import top.madkarma.truecaller.ui.bindings.Navigation

val hideTabs =
    patch(TruecallerPatchStrings.hideTabsName) {
        description(TruecallerPatchStrings.hideTabsDescription)
        compatibleWith("com.truecaller")

        settings(
            truecallerSettings,
            section(
                TruecallerStrings.sectionNavigation,
                TruecallerSettings.hideMessages,
                TruecallerSettings.hideBlocking,
                TruecallerSettings.hidePremium,
                TruecallerSettings.hideFamily,
            ),
        )

        execute {
            // Badge updates still need every item. Filter only after the builder finishes.
            buildBottomBar.after { Navigation.hideTabs(thisObject.field(bottomBarView)) }
            log.info(
                "Tabs: ${buildBottomBar.descriptor} removes hidden tabs and hides a Calls-only bottom bar.",
            )

            currentTab.after { returnValue(Navigation.visibleTab(result)) }
            log.info("Tabs: ${currentTab.descriptor} returns Calls if the current tab is hidden.")

            routeToTab.before {
                val tab = paramOfType(tabType.descriptor)
                tab.assign(Navigation.visibleTab(tab))
            }
            log.info("Tabs: ${routeToTab.descriptor} routes hidden tabs to Calls.")

            callLogHeader.before {
                val visibility = paramOfType(familyVisibilityState.descriptor)
                visibility.assign(Navigation.familyVisibility(visibility))
            }
            log.info(
                "Tabs: ${callLogHeader.descriptor} expands the search bar when Family is hidden.",
            )

            gate(TruecallerSettings.hideFamily) {
                legacyFamilyHeaderEnabled.before { returnValue(Decisions.disabled()) }
            }
            log.info(
                "Tabs: ${legacyFamilyHeaderEnabled.descriptor} hides the Family header in the older layout.",
            )
        }
    }
