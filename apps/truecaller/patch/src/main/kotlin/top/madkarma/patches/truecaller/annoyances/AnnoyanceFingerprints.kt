// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.annoyances

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal val defaultDialerDeepLink =
    klass("com.truecaller.deeplink.handlers.DefaultDialerDeepLinkHandler").method("handleDeepLink")

private val settingsBannerState =
    klass("settingsBannerState") {
        strings("SettingsBannerState(shouldShowBanner=")
    }

internal val settingsPermissionBanner = settingsBannerState.method("<init>")

internal val notificationPermissionBanner =
    method("notificationPermissionBanner") {
        inClass(klass(notificationPromoPriority.owner))
        strings("featureNotificationsPermissionBanner")
    }

private val promoPriority = klass("com.truecaller.promobanners.api.PromoPriority")

private val notificationPromo = promoPriority.field("ENABLE_NOTIFICATION")

private val notificationPromoPriority =
    method("notificationPromoPriority") {
        name("getPriority")
        accesses { name(notificationPromo.name) }
    }

private val whatsappPromo = promoPriority.field("WHATSAPP_NOTIFICATION_ACCESS")

private val permissionPromoPriority =
    method("permissionPromoPriority") {
        accesses { name(whatsappPromo.name) }
        returns(promoPriority.descriptor)
    }

internal val permissionPromosEnabled =
    method("permissionPromosEnabled") {
        inClass(klass(permissionPromoPriority.owner))
        calls { name("valueOf") }
    }

internal val surveyActivities =
    listOf(
            "BottomSheetSurveyActivity",
            "FacsBottomSheetSurveyActivity",
            "FamilyProtectionBottomSheetSurveyActivity",
            "RewardProgramBottomSheetSurveyActivity",
        )
        .map { klass("com.truecaller.surveys.ui.bottomSheet.$it").method("onCreate") }
