// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.inshot.pro

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

// Only the Huawei subscription check reads this preference.
private val isHuaweiSubscriber =
    method("isHuaweiSubscriber") {
        strings("SubscribeProOfHw")
        returns(Type.Boolean)
    }

// The aggregate Pro check (account-bound, Google, or Huawei purchase) that the app's Pro gates,
// item unlocks, and ad checks read. It is the only caller of the Huawei check.
internal val isPro =
    method("isPro") {
        calls(isHuaweiSubscriber)
        inClass(klass(isHuaweiSubscriber.owner))
        returns(Type.Boolean)
        params()
    }

// Binds the "InShot Premium" card at the top of Settings. Its plan subtitle depends on the Pro
// check, which no other method of the delegate reads.
private val subscriptionSettingDelegate =
    klass("subscriptionSettingDelegate") { sourceFile("SettingSubscriptionAdapterDelegate.java") }

internal val bindSubscriptionSetting =
    method("bindSubscriptionSetting") {
        inClass(subscriptionSettingDelegate)
        calls(isPro)
    }

// Settings handles item taps through a touch listener, which hands this method the tapped item.
internal val settingClick =
    method("settingClick") {
        strings("pro_click", "isForceSmoothRealTime")
        hasParam(Type.View)
    }

internal const val VIEW_HOLDER = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;"

internal val itemView = klass(VIEW_HOLDER).field("itemView")
