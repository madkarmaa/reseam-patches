// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Navigation")
@file:Suppress("unused")

package top.madkarma.truecaller.ui

import android.view.View
import android.view.ViewGroup
import app.reseam.runtime.Export
import com.truecaller.bottombar.BottomBarButtonType
import com.truecaller.bottombar.BottomBarButtonType.*
import com.truecaller.bottombar.BottomBarView
import com.truecaller.bottombar.BottomBarView.BottomBarMenuItemId
import com.truecaller.calllog.presentation.ui.screen.model.FamilyOnBoardingVisibilityState
import top.madkarma.truecaller.settings.TruecallerSettings

private fun isHidden(tab: BottomBarButtonType): Boolean =
    when (tab) {
        MESSAGES -> TruecallerSettings.hideMessages.value
        BLOCKING -> TruecallerSettings.hideBlocking.value
        PREMIUM -> TruecallerSettings.hidePremium.value
        FAMILY_PROTECTION_CONFIG -> TruecallerSettings.hideFamily.value
        else -> false
    }

@Export
fun hideTabs(view: Any?) {
    val bottomBar = view as? BottomBarView ?: return
    val menu = bottomBar.menu

    if (isHidden(MESSAGES)) menu.removeItem(BottomBarMenuItemId.MESSAGES.id)
    if (isHidden(BLOCKING)) menu.removeItem(BottomBarMenuItemId.BLOCKING.id)
    if (isHidden(PREMIUM)) menu.removeItem(BottomBarMenuItemId.PREMIUM.id)
    if (isHidden(FAMILY_PROTECTION_CONFIG))
        menu.removeItem(BottomBarMenuItemId.FAMILY_PROTECTION_CONFIG.id)

    // A bar left with only Calls has nothing to switch to.
    if (menu.size() > 1) return
    collapseToInsets(bottomBar)
    bottomBarDivider(bottomBar)?.visibility = View.GONE
}

// The bar pads itself by the navigation-bar inset; keeping that padding stops content from
// sliding under the system navigation buttons.
private fun collapseToInsets(bottomBar: ViewGroup) {
    for (index in 0 until bottomBar.childCount) bottomBar.getChildAt(index).visibility = View.GONE
    bottomBar.minimumHeight = 0
}

private fun bottomBarDivider(bottomBar: View): View? {
    val dividerId =
        bottomBar.resources.getIdentifier(
            "bottom_bar_revamp_divider",
            "id",
            bottomBar.context.packageName,
        )
    if (dividerId == 0) return null
    return (bottomBar.parent as? View)?.findViewById(dividerId)
}

@Export
fun visibleTab(tab: BottomBarButtonType?): BottomBarButtonType? =
    if (tab != null && isHidden(tab)) CALLS else tab

@Export
fun familyVisibility(original: FamilyOnBoardingVisibilityState?): FamilyOnBoardingVisibilityState? =
    if (TruecallerSettings.hideFamily.value) FamilyOnBoardingVisibilityState.HIDDEN else original
