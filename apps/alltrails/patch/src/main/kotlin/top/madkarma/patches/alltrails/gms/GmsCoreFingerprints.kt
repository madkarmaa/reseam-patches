// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.alltrails.gms

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val authActivityCreate =
    klass("com.alltrails.alltrails.ui.authentication.AuthActivity").method("onCreate")

internal val signInIntentLauncher = method("signInIntentLauncher") {
    inClass(klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))
    strings("com.google.android.gms.auth.GOOGLE_SIGN_IN", "com.google.android.gms")
}

private val serviceClient = klass("serviceClient") {
    strings("Calling connect() while still connected, missing disconnect() for ")
}

private val serviceConfiguration = serviceClient.method("getEndpointPackageName").field {
    owner(serviceClient.descriptor)
}

internal val serviceConfigurationConstructor = method("serviceConfigurationConstructor") {
    inClass(klass(serviceConfiguration.type))
    name("<init>")
}

internal val serviceAction = serviceClient.method("getStartServiceAction")
internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
