// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.iconpackstudio.gms

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

internal val homeActivityCreate =
    klass("ginlemon.iconpackstudio.editor.homeActivity.HomeActivity").method("onCreate")

internal val signInActivityCreate =
    klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity").method("onCreate") {
        strings("com.google.android.gms")
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

// These literals are account types, including Account parcel reconstruction and default accounts.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }

internal val credentialProviderFromManifest = method("credentialProviderFromManifest") {
    strings("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
}

internal val selectCredentialProvider = method("selectCredentialProvider") {
    calls(credentialProviderFromManifest)
    inClass(klass(credentialProviderFromManifest.owner))
}

internal val credentialsAvailable =
    klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl").method("isAvailableOnDevice")
