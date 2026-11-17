// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("NativeBridge")
@file:Suppress("unused")

package top.madkarma.mtmanager.vip

import android.util.Log
import app.reseam.runtime.Export

@Export
@Synchronized
fun install(owner: String, nativeMethod: String, replacement: String) {
    System.loadLibrary("mtvip")

    val targetClass = Class.forName(owner)
    forwardBooleanMethod(targetClass, nativeMethod, replacement)

    check(targetClass.getDeclaredMethod(nativeMethod).invoke(null) == true) {
        "Native VIP forwarding failed"
    }
    Log.i("MTManagerVIP", "Native VIP forwarding installed")
}

private external fun forwardBooleanMethod(
    owner: Class<*>,
    nativeMethod: String,
    replacement: String,
)
