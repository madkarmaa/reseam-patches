// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Rating") @file:Suppress("unused")

package top.madkarma.nyx.annoyances

import android.util.Log
import app.reseam.runtime.Export

@Export
fun skip(callback: Any?, callbackClassName: String, callbackMethodName: String, noReviewUi: Any) {
    Log.i("NyxAnnoyances", "Rating popup skipped")
    if (callback == null) return

    Class.forName(callbackClassName).getMethod(callbackMethodName, noReviewUi.javaClass)
        .invoke(callback, noReviewUi)
}
