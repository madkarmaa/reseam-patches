// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.oto.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.point

// The main screen renders the "Oto Music+" label only when the entitlement is active.
private val plusLabel = method("plusLabel") {
    strings("Oto Music+")
}

// The menu, settings, and Plus dialogs call this to check the entitlement.
internal val isPlus = method("isPlus") {
    calledBy(plusLabel)
    returns(Type.Boolean)
    params()
}

// Reads the entitlement from the cached Plus state.
internal val storedPlusEntitlement = method("storedPlusEntitlement") {
    calledBy(isPlus)
    returns(Type.Boolean)
    params()
}

// Startup reads the APK container tag through the native tag library.
private val readContainerTag = method("readContainerTag") {
    calls(klass("com.piyush.taglib.TagLib").method("containerTag"))
}

// The same class validates the tag, and isPlus requires a valid one.
internal val validContainerTag = method("validContainerTag") {
    inClass(klass(readContainerTag.owner))
    returns(Type.Boolean)
}

// The app stores the entitlement under the song-duration preference key.
private const val PLUS_PREFERENCE = "pref_show_song_duration"

// Creates the cached Plus state from the stored entitlement.
private val plusStateInitializer = method("plusStateInitializer") {
    strings(PLUS_PREFERENCE)
    calls { name("registerOnSharedPreferenceChangeListener") }
}

internal val initialStoredPlus =
    plusStateInitializer.point { string(PLUS_PREFERENCE) }.next { resultOf(Type.Boolean) }

// Reloads the cached Plus state when the entitlement preference changes.
internal val plusStateListener = method("plusStateListener") {
    strings(PLUS_PREFERENCE)
    name("onSharedPreferenceChanged")
}
