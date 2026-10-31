// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.liftoff.gms

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val tokenServiceInitializer = method("tokenServiceInitializer") {
    strings("com.google.android.gms.auth.GetToken")
    name("<clinit>")
}

// This SDK's modern token API is a stub in GmsCore; its GetToken service is implemented.
internal val modernTokenApiAvailable = method("modernTokenApiAvailable") {
    inClass(klass(tokenServiceInitializer.owner))
    returns(Type.Boolean)
    calls { name("getApplicationInfo") }
}

private val serviceConnectionUpdate = method("serviceConnectionUpdate") {
    strings("Calling connect() while still connected, missing disconnect() for ")
}

// Takes the service package, service action, an unused flag, bind flags, and the dynamic-lookup flag.
internal val serviceConfigurationConstructor = method("serviceConfigurationConstructor") {
    name("<init>")
    params(Type.String, Type.String, Type.Boolean, Type.Int, Type.Boolean)
    calledBy(serviceConnectionUpdate)
}

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
