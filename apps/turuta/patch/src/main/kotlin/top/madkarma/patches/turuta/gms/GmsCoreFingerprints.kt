// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.turuta.gms

import app.reseam.patch.*

internal val mainActivityCreate = klass("pe.tumicro.android.MainActivity").method(
    "onCreate", inherited = true
) { params("android.os.Bundle") }

internal val signInActivityCreate =
    klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity").method("onCreate") {
        strings("com.google.android.gms")
    }

internal val tokenServiceInitializer = method("tokenServiceInitializer") {
    strings("GoogleAuthUtil", "com.google.android.gms.auth.GetToken")
    name("<clinit>")
}

// This SDK's modern token API is a stub in GmsCore; its GetToken service is implemented.
internal val modernTokenApiAvailable = method("modernTokenApiAvailable") {
    inClass(klass(tokenServiceInitializer.owner))
    returns(Type.Boolean)
    calls { name("getApplicationInfo") }
}

internal val serviceConfigurationConstructor = method("serviceConfigurationConstructor") {
    name("<init>")
    params(Type.String, Type.Boolean)
    strings("com.google.android.gms")
}

internal val servicePackage = serviceConfigurationConstructor.point {
    string("com.google.android.gms")
}

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
