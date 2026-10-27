// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.premium

import app.reseam.patch.Type
import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

private val subscription = klass("com.vega.subscribe.SubscribeImpl")
private val editorSubscription = klass("com.lemon.editor.proxy.PayVipImpl")
private val cloudSubscription = klass("com.lemon.editor.proxy.SubscribeCloudImpl")
private val userMembership = klass("com.lm.components.subscribe.config.UserVipInfo")
private val accountDetails = klass("com.lemon.lv.clipmonetize.data.UserDetailInfo")
private val commerce = klass("com.lemon.lv.clipmonetize.wrapper.BusinessManager")

private val membershipState = klass("membershipState") {
    enumConstants("UNKNOWN", "GENERAL", "VIP", "SVIP", "SSVIP", "VIP4")
}

internal val highestMembership = membershipState.enumConstant("VIP4")

internal val openLynxView =
    klass("com.lm.components.lynx.bridge.handler.LynxCommonBridge").method("viewOpen")

internal val homeSubscriptionButton = method("homeSubscriptionButton") {
    strings("updateHomeProIv, show: ")
}

internal val lynxSuccessResponse = method("lynxSuccessResponse") {
    strings("callbackSuccessToJs: response-> ")
}

internal val lynxExperimentConfiguration = method("lynxExperimentConfiguration") {
    strings("cc_profile_home_vip_card_opt")
    returns(Type.String)
}

internal val updateLynxTranslations =
    klass($$"com.lynx.tasm.LynxTemplateRender$TASMCallback").method("onUpdateI18nResource")

private val watermarkBenefitLabel = method("watermarkBenefitLabel") {
    strings("monthly_trial", "lifetime_trial")
}

internal val watermarkBenefit = method("watermarkBenefit") {
    calledBy(watermarkBenefitLabel)
    returns("kotlin.Pair")
}

private val watermarkBenefitType = klass("watermarkBenefitType") {
    enumConstants("LONG_TERM_FREE", "MONTHLY_TRIAL")
}

internal val premiumWatermarkBenefit = watermarkBenefitType.enumConstant("VIP")

private val hasAnyMembership = membershipState.method("isAnyVIP")

private val accountHasPremium = method("accountHasPremium") {
    inClass(commerce)
    calls(hasAnyMembership)
}

// The only boolean-input predicate checks SVIP, SSVIP and VIP4 membership.
private val accountHasHigherTier = method("accountHasHigherTier") {
    inClass(commerce)
    params(Type.Boolean)
    returns(Type.Boolean)
}

internal val accountMembershipLevel = method("accountMembershipLevel") {
    inClass(commerce)
    params()
    returns(membershipState.descriptor)
}

internal val premiumChecks = listOf(
    subscription.method("isVip"),
    editorSubscription.method("isVip"),
    cloudSubscription.method("isVip"),
    userMembership.method("isVipUser"),
    userMembership.method("getFlag"),
    accountDetails.method("isVipUser"),
    accountDetails.method("getFlag"),
    accountHasPremium,
    accountHasHigherTier,
)
