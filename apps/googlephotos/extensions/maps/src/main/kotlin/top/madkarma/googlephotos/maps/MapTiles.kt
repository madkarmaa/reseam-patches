// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("MapTiles") @file:Suppress("unused")

package top.madkarma.googlephotos.maps

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export
import top.madkarma.googlephotos.settings.GooglePhotosSettings

private const val TAG = "ReseamPhotos"
private const val MAPBOX = "com.mapbox.mapboxsdk.Mapbox"

// Any non-empty key lets the renderer resolve its styles; it never leaves the device while offline.
private const val OFFLINE_KEY = "reseam-offline"

@Volatile
private var mapsClassLoader: ClassLoader? = null

@Export
fun rememberMapsContext(context: Context?) {
    if (context == null) return
    mapsClassLoader = context.classLoader
}

// GmsCore's Mapbox renderer aborts the app when a style needs a key, and resets the key to "" for every map.
@Export
fun applyAccessToken() {
    val loader = mapsClassLoader ?: return
    try {
        val mapbox = loader.loadClass(MAPBOX)
        if (mapbox.getMethod("hasInstance").invoke(null) != true) return

        val token = GooglePhotosSettings.mapboxAccessToken.value.trim()
        val key = token.ifEmpty { OFFLINE_KEY }
        if (mapbox.getMethod("getApiKey").invoke(null) == key) return

        mapbox.getMethod("setApiKey", String::class.java).invoke(null, key)
        // Without a real token the renderer stays offline, so the placeholder is never sent to Mapbox.
        val connected = if (token.isEmpty()) false else null
        mapbox.getMethod("setConnected", Boolean::class.javaObjectType).invoke(null, connected)
        Log.i(
            TAG,
            if (token.isEmpty()) "Maps render offline without tiles" else "Maps use the Mapbox access token"
        )
    } catch (_: ClassNotFoundException) {
        // This GmsCore renders maps without Mapbox and needs no key.
        mapsClassLoader = null
    } catch (exception: ReflectiveOperationException) {
        Log.w(TAG, "Cannot configure the GmsCore map renderer", exception)
    }
}
