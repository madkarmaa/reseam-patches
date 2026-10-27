// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.templates

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val templateWatermarkConfiguration = klass("templateWatermarkConfiguration") {
    strings("CcTemplateLeWatermarkAbConfig(enable=")
}

internal val templateWatermarkConfigurationApplies =
    method("templateWatermarkConfigurationApplies") {
        inClass(templateWatermarkConfiguration)
        strings("template_edit", "-1")
    }

internal val templateExportWithoutWatermark = method("templateExportWithoutWatermark") {
    inClass(templateWatermarkConfiguration)
    params(Type.String, Type.String)
    accesses { name("disableExportAddWatermark") }
}

internal val templatePreviewWithoutWatermark = method("templatePreviewWithoutWatermark") {
    inClass(templateWatermarkConfiguration)
    params(Type.String, Type.String)
    accesses { name("disableLeWatermark") }
}

internal val templatePurchasePreflight = method("templatePurchasePreflight") {
    strings("template_use_pay", "template_vip_material_export_popup", "business_template_pay")
    hasParam("kotlin.jvm.functions.Function0")
    returns(Type.Void)
}

internal val templateExportEntitlement = method("templateExportEntitlement") {
    inClass(klass(templatePurchasePreflight.owner))
    returns(Type.Boolean)
    calls {
        owner("com.vega.subscriptionapi.biz.function.IBusinessTemplateExportFunction")
        returns(Type.Boolean)
    }
}

private val materialExportPreflight = method("materialExportPreflight") {
    strings("export blocked: strategy_unavailable, scene=")
}

private val materialPaymentChecker = klass("materialPaymentChecker") {
    strings("VipExportChecker")
}

private val radicalMaterialPaymentChecker = klass("radicalMaterialPaymentChecker") {
    strings("RadicalVipExportChecker")
}

private val materialPaymentConstructor = materialPaymentChecker.method("<init>")
private val radicalMaterialPaymentConstructor = radicalMaterialPaymentChecker.method("<init>")

internal val materialExportCheckers = method("materialExportCheckers") {
    inClass(klass(materialExportPreflight.owner))
    calls(materialPaymentConstructor)
    calls(radicalMaterialPaymentConstructor)
}
