// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.verification

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val smsApiConfiguration = method("smsApiConfiguration") {
    strings("verificationOtpSmsApi_19731")
    name("invoke")
}

internal val verificationSetup = klass("com.truecaller.wizard.WizardActivity").method("onCreate")

internal val readIntegerSetting = method("readIntegerSetting") {
    calledBy(smsApiConfiguration)
    params(Type.Int, Type.String)
}
