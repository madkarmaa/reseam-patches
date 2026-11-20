// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.export

import app.reseam.patch.*
import top.madkarma.patches.capcut.CapCutPatchStrings
import top.madkarma.patches.capcut.premium.unlockPremium

private const val FUNCTION1 = "kotlin.jvm.functions.Function1"
private const val BUSINESS_RESULT = "com.vega.subscriptionapi.core.Result"
private const val COMPLETABLE_JOB = "kotlinx.coroutines.CompletableJob"

val unlockUltraHdExport =
    patch(CapCutPatchStrings.unlockUltraHdExportName) {
        description(CapCutPatchStrings.unlockUltraHdExportDescription)
        compatibleWith("com.lemon.lvoverseas")
        dependsOn(unlockPremium)

        execute {
            resolutionRequiresPurchase.alwaysReturn(false)
            log.info(
                "UHD: ${resolutionRequiresPurchase.descriptor} no longer requires purchasing a resolution benefit.",
            )

            // The export-success callback also uses this fallback to update the next selected
            // resolution.
            resolutionFallback.replace { returnValue(paramOfType(Type.Int)) }
            log.info(
                "UHD: ${resolutionFallback.descriptor} preserves the requested resolution before and after export.",
            )

            templateUltraHdEntitlement.alwaysReturn(true)
            log.info(
                "UHD: ${templateUltraHdEntitlement.descriptor} accepts the local template UHD entitlement.",
            )

            templateUltraHdAvailable.alwaysReturn(true)
            log.info(
                "UHD: ${templateUltraHdAvailable.descriptor} exposes the existing template UHD control.",
            )

            // The callback is the caller's completion handler; a default Result reports success
            // (code 0), the same value the use flow sends after server verification succeeds.
            templateUltraHdUse.replace {
                paramOfType(FUNCTION1)
                    .callInterface(
                        FUNCTION1,
                        "invoke",
                        proto(Type.Object, Type.Object),
                        newInstance(BUSINESS_RESULT),
                    )

                val finished =
                    callStatic(
                        "kotlinx.coroutines.JobKt",
                        $$"Job$default",
                        proto(COMPLETABLE_JOB, "kotlinx.coroutines.Job", Type.Int, Type.Object),
                        nullObject,
                        int(1),
                        nullObject,
                    )
                finished.callInterface(COMPLETABLE_JOB, "complete", proto(Type.Boolean))

                returnValue(finished)
            }
            log.info(
                "UHD: ${templateUltraHdUse.descriptor} reports a successful template UHD use without trials or purchases.",
            )
        }
    }
