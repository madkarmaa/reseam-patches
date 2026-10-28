// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.vn.gms

import app.reseam.patch.*

internal val loginActivityCreate =
    klass("com.frontrow.account.ui.login.LoginActivity").method("onCreate")

internal val signInIntentLauncher = method("signInIntentLauncher") {
    inClass(klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))
    strings("com.google.android.gms.auth.GOOGLE_SIGN_IN", "com.google.android.gms")
}

// VN inlines the token API selection; its GetToken fallback binds this component.
internal val tokenServiceInitializer = method("tokenServiceInitializer") {
    strings("GoogleAuthUtil", "com.google.android.gms.auth.GetToken")
    name("<clinit>")
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
