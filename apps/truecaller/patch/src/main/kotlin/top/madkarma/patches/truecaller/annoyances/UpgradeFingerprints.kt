// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.annoyances

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val interstitialConfiguration =
    klass("com.truecaller.premium.data.component.interstitial.InterstitialSpec")

private val interstitialEnabled = interstitialConfiguration.method("enabled")

internal val automaticUpgradeEligibility = method("automaticUpgradeEligibility") {
    calls(interstitialEnabled)
    // The other caller is a generated coroutine state machine, rather than the eligibility function.
    custom { name != "invokeSuspend" }
}

internal val automaticUpgradeDecision = method("automaticUpgradeDecision") {
    calls(automaticUpgradeEligibility)
    hasParam(Type.String)
}
