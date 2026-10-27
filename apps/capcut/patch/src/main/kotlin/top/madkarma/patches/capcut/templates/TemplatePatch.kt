// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.templates

import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.capcut.templates.bindings.ExportCheckers
import top.madkarma.patches.capcut.integrity.bypassIntegrityChecks
import top.madkarma.patches.capcut.premium.unlockPremium

val unlockPremiumTemplates = patch("Unlock premium effects and templates") {
    description("Allows local premium effects and template export.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(bypassIntegrityChecks, unlockPremium)

    execute {
        val callbackType = "Lkotlin/jvm/functions/Function0;"
        // paramOfType selects the first match; another Function0 would make this hook ambiguous.
        if (templatePurchasePreflight.parameterTypes.count { it == callbackType } != 1) error("Expected one template export continuation in ${templatePurchasePreflight.descriptor}")

        materialExportCheckers.after { returnValue(ExportCheckers.withoutPaymentCheckers(result)) }
        log.info("Effects: ${materialExportCheckers.descriptor} keeps export readiness, delay and callbacks while omitting both purchase checkers.")

        // This dispatcher only chooses a purchase flow; the callback owns the actual export.
        templatePurchasePreflight.before {
            paramOfType(callbackType).callInterface(callbackType, "invoke", "()Ljava/lang/Object;")
            returnVoid()
        }
        log.info("Templates: ${templatePurchasePreflight.descriptor} continues export without entering a purchase flow.")

        templateExportEntitlement.alwaysReturn(true)
        log.info("Templates: ${templateExportEntitlement.descriptor} enables the export action for premium templates.")

        templateWatermarkConfigurationApplies.alwaysReturn(true)
        log.info("Templates: ${templateWatermarkConfigurationApplies.descriptor} applies the watermark configuration to template exports.")

        templateExportWithoutWatermark.alwaysReturn(true)
        log.info("Templates: ${templateExportWithoutWatermark.descriptor} disables the watermark added when saving templates to the device.")

        templatePreviewWithoutWatermark.alwaysReturn(true)
        log.info("Templates: ${templatePreviewWithoutWatermark.descriptor} disables the template preview watermark.")
    }
}
