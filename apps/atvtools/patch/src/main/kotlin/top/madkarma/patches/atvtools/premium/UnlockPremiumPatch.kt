// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.atvtools.premium

import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.className
import app.reseam.patch.patch
import java.util.Base64
import top.madkarma.atvtools.premium.bindings.Premium
import top.madkarma.patches.atvtools.AtvToolsPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.NativeSignatures

val unlockPremium =
    patch(AtvToolsPatchStrings.unlockPremiumName) {
        description(AtvToolsPatchStrings.unlockPremiumDescription)
        compatibleWith("dev.vodik7.atvtools")
        dependsOn(spoofApkFileAccess)

        execute {
            val packageName = manifest.packageName ?: error("Application package is missing")
            val signature = files.signers().firstOrNull() ?: error("Original app signer is missing")
            val encodedSignature = Base64.getEncoder().encodeToString(signature)

            applicationInitializer.before {
                NativeSignatures.initializeBeforeNativeLibrary(
                    string(packageName),
                    string(encodedSignature),
                    string("liba.so"),
                )
            }
            log.info(
                "Startup: ${applicationInitializer.descriptor} prepares original APK access before application initialization and native library loading.",
            )

            if (purchaseConstructor.proto != "(Ljava/lang/String;Ljava/lang/String;)V")
                error("Unsupported purchase constructor: ${purchaseConstructor.descriptor}")

            billingConstructor.after {
                thisObject.set(
                    premiumState,
                    Premium.state(string(className(purchaseConstructor.owner)))
                        .cast(premiumState.type),
                )
            }
            log.info(
                "Premium: ${billingConstructor.descriptor} exposes permanent premium access through ${premiumState.name}.",
            )

            premiumScreen.after { Premium.showAttribution(thisObject, string(PATCH_ATTRIBUTION)) }
            log.info(
                "Premium: ${premiumScreen.descriptor} shows the shared patch attribution instead of purchase options.",
            )
        }
    }
