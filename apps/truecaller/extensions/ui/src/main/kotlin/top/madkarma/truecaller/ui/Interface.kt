// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Interface") @file:Suppress("unused")

package top.madkarma.truecaller.ui

import android.app.Activity
import app.reseam.runtime.Export

@Export
fun finishActivity(activity: Activity?) {
    activity?.finish()
}
