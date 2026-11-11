// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.verification

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.patch
import top.madkarma.patches.truecaller.TruecallerPatchStrings
import top.madkarma.patches.truecaller.shared.disableFeatureFlag
import top.madkarma.patches.truecaller.shared.featureFlagOverrides
import top.madkarma.truecaller.verification.bindings.SmsVerification

val useSmsVerification =
    patch(TruecallerPatchStrings.useSmsVerificationName) {
        description(
            TruecallerPatchStrings.useSmsVerificationDescription,
        )
        compatibleWith("com.truecaller")
        dependsOn(featureFlagOverrides)
        enabledByDefault(false)

        execute {
            appEntry { SmsVerification.start(application) }
            verificationSetup.after { SmsVerification.prepare(thisObject) }
            log.info(
                "SMS verification: ${verificationSetup.descriptor} requests SMS permission during setup.",
            )

            readIntegerSetting.after {
                returnValue(SmsVerification.overrideApi(paramOfType(Type.String), result))
            }
            log.info(
                "SMS verification: ${readIntegerSetting.descriptor} selects Android SMS reception when permission is granted.",
            )
            disableFeatureFlag("verificationOtpIgnoreSmsTemplate_57429")
        }
    }
