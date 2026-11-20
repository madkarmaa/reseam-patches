// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.ilovepdf.gms

import app.reseam.patch.*

private val serviceClient =
    klass("serviceClient") {
        strings("Failed to connect when checking package")
    }

private val serviceConfiguration =
    serviceClient.method("getEndpointPackageName").field {
        owner(serviceClient.descriptor)
    }

internal val serviceConfigurationConstructor =
    method("serviceConfigurationConstructor") {
        inClass(klass(serviceConfiguration.type))
        name("<init>")
    }

internal val serviceAction = serviceClient.method("getStartServiceAction")
internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

// "com.google" is also a stack-trace package prefix here; account types build Accounts.
internal val googleAccountTypes =
    methods("googleAccountTypes") {
        strings("com.google")
        calls {
            owner("android.accounts.Account")
            name("<init>")
        }
    }

// Google Drive's account manager lists and invalidates accounts by type without building them.
internal val googleAccountLookups =
    methods("googleAccountLookups") {
        strings("com.google")
        calls { owner("android.accounts.AccountManager") }
    }

// GoogleAuthUtil accepts accounts of the listed types, and binds its GetToken service by component.
internal val tokenServiceInitializer =
    method("tokenServiceInitializer") {
        strings("GoogleAuthUtil", "com.google.android.gms.auth.GetToken")
        name("<clinit>")
    }

private val credentialProviderFactory = klass("androidx.credentials.CredentialProviderFactory")

internal val manifestCredentialProvider =
    credentialProviderFactory.method("tryCreateClosedSourceProviderFromManifest")

// Both overloads prefer the platform provider on Android 14+.
internal val bestCredentialProviders =
    credentialProviderFactory.methods("bestCredentialProviders") {
        name("getBestAvailableProvider")
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
