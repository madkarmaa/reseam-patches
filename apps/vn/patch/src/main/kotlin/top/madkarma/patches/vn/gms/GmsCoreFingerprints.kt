// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.vn.gms

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.method
import app.reseam.patch.methods

// VN inlines the token API selection; its GetToken fallback binds this component.
internal val tokenServiceInitializer =
    method("tokenServiceInitializer") {
        strings("GoogleAuthUtil", "com.google.android.gms.auth.GetToken")
        name("<clinit>")
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

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
