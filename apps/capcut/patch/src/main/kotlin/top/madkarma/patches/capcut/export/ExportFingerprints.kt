// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.export

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val resolutionBusiness = klass("resolutionBusiness") {
    strings("ResolutionBusinessFunction")
}

internal val resolutionRequiresPurchase = method("resolutionRequiresPurchase") {
    inClass(resolutionBusiness)
    calls { owner("com.lemon.lv.clipmonetize.data.VipState"); name("isAnyVIP") }
}

internal val resolutionFallback = method("resolutionFallback") {
    inClass(resolutionBusiness)
    params(Type.Int)
    returns(Type.Int)
    calls {
        owner(resolutionBusiness.descriptor)
        hasParam("com.lemon.lv.clipmonetize.wrapper.BenefitResource")
        returns(Type.Boolean)
    }
}

private val templateUltraHdBusiness = klass("templateUltraHdBusiness") {
    strings("TemplateExportUltraHdBusinessFunction")
}

internal val templateUltraHdEntitlement = method("templateUltraHdEntitlement") {
    inClass(templateUltraHdBusiness)
    returns(Type.Boolean)
    calls {
        owner("com.lemon.lv.clipmonetize.wrapper.BusinessManager")
        hasParam("com.lemon.lv.clipmonetize.wrapper.BenefitResource")
        returns("com.lemon.lv.clipmonetize.data.VipState")
    }
}

private val templateUltraHdUtilities = klass("templateUltraHdUtilities") {
    strings("sp_export_high_definition_")
}

internal val templateUltraHdUseCheck = method("templateUltraHdUseCheck") {
    inClass(templateUltraHdUtilities)
    params("kotlin.jvm.functions.Function1")
    returns(Type.Void)
}

private val templateUltraHdConfiguration =
    klass("com.vega.subscription.settings.TemplateExportUltraHdConfig")

// The retained field distinguishes availability from bubble and trial-only configuration.
internal val templateUltraHdAvailable = method("templateUltraHdAvailable") {
    inClass(templateUltraHdConfiguration)
    params()
    returns(Type.Boolean)
    accesses { owner(templateUltraHdConfiguration.descriptor); name("enable") }
}
