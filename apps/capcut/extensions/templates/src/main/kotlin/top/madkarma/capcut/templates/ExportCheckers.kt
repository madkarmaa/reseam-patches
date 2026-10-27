// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ExportCheckers") @file:Suppress("unused")

package top.madkarma.capcut.templates

import android.util.Log
import app.reseam.runtime.Export
import com.vega.subscription.core.checker.IBusinessChecker

@Export
fun withoutPaymentCheckers(value: Any?): Any? {
    // Preserve the coroutine suspension marker if the factory ever suspends.
    if (value !is List<*>) return value

    return value.filterNot { checker ->
        if (checker !is IBusinessChecker) return@filterNot false
        val name = checker.name

        val requiresPayment = name == "VipExportChecker" || name == "RadicalVipExportChecker"
        if (requiresPayment) Log.i("CapCutTemplates", "Omitted $name from material export")
        requiresPayment
    }
}
