// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("MapProviders")
@file:Suppress("unused")

package top.madkarma.geotracker.maps

import android.util.Log
import app.reseam.runtime.Export

private const val TAG = "GeoTrackerMaps"

/** [providers] without [google], so Google Maps cannot be chosen. */
@Export
fun withoutGoogle(providers: List<Any?>?, google: Any?): List<Any?> {
    val remaining = providers.orEmpty().filter { it !== google }
    Log.i(TAG, "Map providers without Google Maps: $remaining")
    return remaining
}

/** [provider], or [mapbox] when it is [google]. */
@Export
fun replaceGoogle(provider: Any?, google: Any?, mapbox: Any?): Any? =
    if (provider === google) mapbox else provider
