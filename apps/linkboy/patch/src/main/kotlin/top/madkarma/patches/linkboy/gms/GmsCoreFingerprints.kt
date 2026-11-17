// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.linkboy.gms

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// Drive sync requests its access token from GoogleAuthUtil's GetToken service.
internal val tokenServiceInitializer =
    method("tokenServiceInitializer") {
        strings("com.google.android.gms.auth.GetToken")
        name("<clinit>")
    }

// This SDK's modern token API is a stub in GmsCore; its GetToken service is implemented.
internal val modernTokenApiAvailable =
    method("modernTokenApiAvailable") {
        inClass(klass(tokenServiceInitializer.owner))
        returns(Type.Boolean)
        calls { name("getApplicationInfo") }
    }

internal val serviceConfigurationConstructor =
    method("serviceConfigurationConstructor") {
        name("<init>")
        params(Type.String, Type.Boolean)
        strings("com.google.android.gms")
    }

// The constructor stores the Play Services package as a constant rather than a parameter. The
// supervisor's bind timeout names the stalled service by that package.
internal val servicePackage =
    method("bindTimeout") { strings("Timeout waiting for ServiceConnection callback ") }
        .field("servicePackage") {
            owner(serviceConfigurationConstructor.owner)
            type(Type.String)
        }

// Drive sync picks its account with Play Services' AccountPicker.
internal val accountPickerIntent =
    method("accountPickerIntent") {
        strings("com.google.android.gms.common.account.CHOOSE_ACCOUNT")
    }

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
