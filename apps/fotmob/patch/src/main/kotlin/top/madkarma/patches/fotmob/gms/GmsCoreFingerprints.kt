// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.fotmob.gms

import app.reseam.patch.*

private val serviceClient =
    klass("serviceClient") {
        strings("Calling connect() while still connected, missing disconnect() for ")
    }

internal val serviceAction = serviceClient.method("getStartServiceAction")
internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

private val serviceConnector =
    method("serviceConnector") {
        inClass(serviceClient)
        calls(serviceClient.method("getStartServicePackage"))
    }

// The client copies its configuration into the endpoint that the service supervisor binds. Unlike
// the configuration, which R8 merged into an unrelated class, the endpoint can also name a
// component.
internal val serviceEndpointConstructor =
    method("serviceEndpointConstructor") {
        name("<init>")
        params(Type.String, Type.String, Type.Boolean)
        calledBy(serviceConnector)
        accesses { type("android.content.ComponentName") }
    }

// "com.google" is also a stack-trace package prefix here; account types build Accounts.
internal val googleAccountTypes =
    methods("googleAccountTypes") {
        strings("com.google")
        calls {
            owner("android.accounts.Account")
            name("<init>")
        }
    }

// GoogleAuthUtil accepts accounts of the listed types, and binds its GetToken service by component.
internal val tokenServiceInitializer =
    method("tokenServiceInitializer") {
        strings("GoogleAuthUtil", "com.google.android.gms.auth.GetToken")
        name("<clinit>")
    }

internal val credentialProviderFromManifest =
    method("credentialProviderFromManifest") {
        strings("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
    }

internal val selectCredentialProvider =
    method("selectCredentialProvider") {
        calls(credentialProviderFromManifest)
        inClass(klass(credentialProviderFromManifest.owner))
    }

internal val frameworkCredentialResponseReader =
    method("frameworkCredentialResponseReader") {
        strings("android.service.credentials.extra.GET_CREDENTIAL_RESPONSE")
        calls {
            owner("android.credentials.GetCredentialResponse")
            name("getCredential")
        }
    }

private val credentialProvider =
    klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl")

internal val credentialsAvailable = credentialProvider.method("isAvailableOnDevice") { params() }

// Credential requests use the identity credential service when this check passes, and legacy flows
// otherwise.
internal val identityCredentialsCheck =
    credentialProvider.method("isAvailableOnDevice") { params(Type.Int) }
