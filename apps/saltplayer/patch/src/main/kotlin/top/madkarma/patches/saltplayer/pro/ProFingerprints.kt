// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.saltplayer.pro

import app.reseam.patch.FieldTarget
import app.reseam.patch.MethodTarget
import app.reseam.patch.Type
import app.reseam.patch.classTarget
import app.reseam.patch.dex.fieldRef
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// Turns off the Pro-only settings and deletes the custom main screen background.
private val clearProSettings =
    method("clearProSettings") {
        strings("main_screen_background_cover")
        returns(Type.Void)
    }

// Holds every app setting, purchases included, each backed by its own field.
private val settings = classTarget("settings") { clearProSettings.method.classDef }

// Pro is stored as one flag per purchase channel (Play Store and official activation). Each setter
// clears the Pro-only settings when its flag is revoked.
internal val proFlagSetters =
    methods("proFlagSetters") {
        calls(clearProSettings)
        params(Type.Boolean)
    }

// Only the Play Store flag has a getter in the settings class; the combined Pro check every
// feature reads calls it and reads the official activation flag itself.
internal val playStoreProGetter =
    settings
        .methods("settingsGetters") {
            returns(Type.Boolean)
            params()
        }
        .single {
            val flags = proFlagSetters.all.map { it.storedFlag.ref }
            method.instructions.any { it.fieldRef in flags }
        }

// Each Pro flag is a settings field holding the flag's delegate. A setter stores its new value
// through the only app method it calls that takes the boxed value, declared by the delegate's
// class.
private val MethodTarget.storedFlag: FieldTarget
    get() {
        val storeValue = method {
            calledBy(this@storedFlag)
            hasParam("java.lang.Object")
        }
        return field {
            owner(settings.descriptor)
            type(storeValue.owner)
        }
    }

private val usbExclusiveUiState =
    klass("usbExclusiveUiState") {
        strings("UsbExclusiveModeUiState(audiophilePack=")
    }

private val buildUsbExclusiveUiState =
    method("buildUsbExclusiveUiState") {
        calls(usbExclusiveUiState.method("<init>"))
    }

// The USB exclusive screen state reads a single setting: Audiophile Pack ownership.
internal val audiophilePackGetter =
    method("audiophilePackGetter") {
        inClass(settings)
        calledBy(buildUsbExclusiveUiState)
    }
