// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.yazio.gms

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// The client keeps its member names, but this build strips its connect() diagnostics.
private val serviceClient = klass("serviceClient") {
    strings("Failed to connect when checking package")
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

// "com.google" is also a stack-trace package prefix here; account types build Accounts.
internal val googleAccountTypes = methods("googleAccountTypes") {
    strings("com.google")
    calls {
        owner("android.accounts.Account")
        name("<init>")
    }
}

internal val credentialProviderFromManifest = method("credentialProviderFromManifest") {
    strings("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
}

internal val selectCredentialProvider = method("selectCredentialProvider") {
    calls(credentialProviderFromManifest)
    inClass(klass(credentialProviderFromManifest.owner))
}

internal val frameworkCredentialResponseReader = method("frameworkCredentialResponseReader") {
    strings("android.service.credentials.extra.GET_CREDENTIAL_RESPONSE")
    calls {
        owner("android.credentials.GetCredentialResponse")
        name("getCredential")
    }
}

internal val credentialsAvailable =
    klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl").method("isAvailableOnDevice") {
        params()
    }
