// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.zoiper.combo

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val comboMonthlyProduct =
    klass("comboMonthlyProduct") {
        strings("combo_monthly_pref_key")
    }

// The only boolean check that builds the monthly combo product is the "either combo plan is
// valid" subscription check.
private val comboSubscriptionValid =
    method("comboSubscriptionValid") {
        calls(comboMonthlyProduct.method("<init>"))
        params()
        returns(Type.Boolean)
    }

// Combines the config flag, the Play subscription, and certificate activation; every feature gate
// reads this.
internal val comboEnabled =
    method("comboEnabled") {
        calls(comboSubscriptionValid)
        params()
        returns(Type.Boolean)
    }
