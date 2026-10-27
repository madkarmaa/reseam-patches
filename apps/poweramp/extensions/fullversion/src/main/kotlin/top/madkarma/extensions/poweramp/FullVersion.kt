// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("FullVersion") @file:Suppress("unused")

package top.madkarma.extensions.poweramp

import android.util.Log
import app.reseam.runtime.Export
import com.maxmpz.audioplayer.Sync

private val readingLicenseStatus = ThreadLocal<Boolean>()
private var fullVersionState: Any? = null
private var pendingExpiry: Any? = null

@Export
fun initialize(status: Any?, expiry: Any?) {
    fullVersionState = status
    pendingExpiry = expiry
    Log.i("PowerampFullVersion", "Lifetime license hooks initialized")
}

@Export
fun licenseInteger(preference: Any?, value: Int): Int = when {
    preference === fullVersionState -> 229
    preference === pendingExpiry -> 0
    else -> value
}

@Export
fun acquire(tag: String?) {
    val bypass = tag == "ps"
    readingLicenseStatus.set(bypass)
    if (!bypass) Sync.native_acquire(tag)
}

@Export
fun close(count: Int) {
    if (readingLicenseStatus.get() != true) Sync.native_close(count)
    readingLicenseStatus.remove()
}

@Export
fun label(index: Int): String? {
    if (readingLicenseStatus.get() != true) return Sync.native_lock(index)
    return when (index) {
        -3 -> "Full Version"
        -2 -> "Lifetime license"
        else -> null
    }
}

@Export
fun release(value: Any?, command: Int): Int =
    if (readingLicenseStatus.get() == true) 0 else Sync.native_release(value, command)
