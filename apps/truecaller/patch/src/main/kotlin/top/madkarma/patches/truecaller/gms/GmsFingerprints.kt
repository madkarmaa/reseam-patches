// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.gms

import app.reseam.patch.*

private val serviceClient = klass("com.google.android.gms.common.internal.BaseGmsClient")

private val serviceEndpoint =
    serviceClient.method("getEndpointPackageName").field { owner(serviceClient.descriptor) }

internal val serviceEndpointConstructor = klass(serviceEndpoint.type).method("<init>")

internal val serviceAction = serviceClient.method("getStartServiceAction")

internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

internal val playServicesAvailability = method("playServicesAvailability") {
    strings("com.google.android.gms.version")
    params(Type.Context, Type.Int)
}

internal val bundledCredentialProvider = method("bundledCredentialProvider") {
    strings("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
}

internal val selectCredentialProvider = method("selectCredentialProvider") {
    calls(bundledCredentialProvider)
}

internal val credentialRequest = klass("credentialRequest") {
    strings("credentialOptions should not be empty")
}

internal val credentialOptions = credentialRequest.fieldOfType(Type.List)

internal val getBundledCredential = method("getBundledCredential") {
    inClass(klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl"))
    name("onGetCredential")
    hasParam(credentialRequest.descriptor)
}

internal val googleOptions = listOf(
    "com.google.android.libraries.identity.googleid.BUNDLE_KEY_FILTER_BY_AUTHORIZED_ACCOUNTS",
    "com.google.android.libraries.identity.googleid.siwg.BUNDLE_KEY_SERVER_CLIENT_ID",
).map { key ->
    val requestData = method("googleOptionData") { strings(key) }
    klass(method("googleOptionConstructor") { calls(requestData) }.owner)
}

internal val credentialProviderAvailability =
    klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl").method("isAvailableOnDevice") {
        params(Type.Int)
    }
