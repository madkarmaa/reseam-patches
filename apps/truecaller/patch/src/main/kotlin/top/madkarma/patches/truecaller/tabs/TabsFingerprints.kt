// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.tabs

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal val tabType = klass("com.truecaller.bottombar.BottomBarButtonType")

internal val buildBottomBar =
    method("buildBottomBar") {
        params("android.util.SparseIntArray")
    }

internal val currentTab =
    method("currentTab") {
        inClass(klass(buildBottomBar.owner))
        calls { name("getCurrentButton") }
    }

// The current-tab getter reads only its bottom-bar view.
internal val bottomBarView = currentTab.field("bottomBarView") {}

internal val routeToTab =
    method("routeToTab") {
        params(tabType.descriptor, $$"com.truecaller.floatingtoolbar.api.CallHistoryTab$Type")
    }

internal val legacyFamilyHeaderEnabled =
    method("legacyFamilyHeaderEnabled") {
        strings("familyGroupApi")
        returns("java.lang.Boolean")
    }

internal val familyVisibilityState =
    klass("com.truecaller.calllog.presentation.ui.screen.model.FamilyOnBoardingVisibilityState")

private val familySearchBar =
    method("familySearchBar") {
        strings("FamilyOnBoardingTransition")
    }

internal val callLogHeader =
    method("callLogHeader") {
        calls(familySearchBar)
        hasParam(familyVisibilityState.descriptor)
    }
