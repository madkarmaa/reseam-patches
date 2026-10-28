// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.gms

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val mainActivityCreate =
    klass("com.socialin.android.photo.picsinphoto.MainPagerActivity").method("onCreate")

internal val signInIntentLauncher = method("signInIntentLauncher") {
    inClass(klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))
    strings("com.google.android.gms.auth.GOOGLE_SIGN_IN", "com.google.android.gms")
}

// This build keeps the Play services client names.
private val serviceClient = klass("com.google.android.gms.common.internal.BaseGmsClient")

private val serviceConfiguration = serviceClient.method("getEndpointPackageName").field {
    owner(serviceClient.descriptor)
}

internal val serviceConfigurationConstructor = method("serviceConfigurationConstructor") {
    inClass(klass(serviceConfiguration.type))
    name("<init>")
}

internal val serviceAction = serviceClient.method("getStartServiceAction")
internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

// Analytics also loads "com.google" as a package prefix; account types build Accounts.
internal val googleAccountTypes = methods("googleAccountTypes") {
    strings("com.google")
    calls {
        owner("android.accounts.Account")
        name("<init>")
    }
}
