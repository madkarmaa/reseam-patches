// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.radarbot.gms

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

private val serviceClient =
    klass("serviceClient") {
        strings("Calling connect() while still connected, missing disconnect() for ")
    }

private val serviceConfiguration =
    serviceClient.method("getEndpointPackageName").field {
        owner(serviceClient.descriptor)
    }

// R8 merged the configuration with an unrelated holder, so its constructor also carries a selector.
// Only the service client passes an authentication action, which is all the hook redirects.
internal val serviceConfigurationConstructor =
    method("serviceConfigurationConstructor") {
        inClass(klass(serviceConfiguration.type))
        name("<init>")
    }

internal val serviceAction = serviceClient.method("getStartServiceAction")
internal val requiresPlayServices = serviceClient.method("requiresGooglePlayServices")

// Other "com.google" literals name SDK packages in ads and telemetry filters, not account types.
internal val googleAccountTypes =
    methods("googleAccountTypes") {
        strings("com.google")
        calls {
            owner("android.accounts.Account")
            name("<init>")
        }
    }

private val credentialProviderFactory =
    klass("credentialProviderFactory") {
        strings("androidx.credentials.CREDENTIAL_PROVIDER_KEY")
    }

// The Google Play services provider the app declares in its manifest.
internal val manifestCredentialProvider =
    credentialProviderFactory.method("tryCreateClosedSourceProviderFromManifest")

// On Android 14+ this picks the platform Credential Manager, and the app's callers disable the
// fallback.
internal val bestAvailableProvider =
    method("bestAvailableProvider") {
        inClass(credentialProviderFactory)
        name("getBestAvailableProvider")
        params(Type.Boolean)
    }

// Both overloads compare the Google Play services version, which GmsCore does not report.
internal val credentialsAvailable =
    methods("credentialsAvailable") {
        inClass(klass("androidx.credentials.playservices.CredentialProviderPlayServicesImpl"))
        name("isAvailableOnDevice")
    }

// GmsCore answers Credential Manager requests with the Bundle extras of the pre-Android 14 format,
// while on Android 14+ the app only reads the platform response and exception objects.
private fun credentialResultReader(name: String, extraReader: String) =
    method("${name}From$extraReader") {
        name(name)
        calls {
            owner("android.content.Intent")
            name(extraReader)
        }
    }

private val bundleResponseReader =
    credentialResultReader("extractGetCredentialResponse", "getBundleExtra")
private val bundleExceptionReader =
    credentialResultReader("extractGetCredentialException", "getBundleExtra")

internal val platformResponseReader =
    credentialResultReader("extractGetCredentialResponse", "getParcelableExtra")
internal val platformExceptionReader =
    credentialResultReader("extractGetCredentialException", "getSerializableExtra")

// The static entry points that read the Bundle extras.
internal val bundleResponse =
    method("bundleResponse") {
        name("extractGetCredentialResponse")
        calls(bundleResponseReader)
    }
internal val bundleException =
    method("bundleException") {
        name("extractGetCredentialException")
        calls(bundleExceptionReader)
    }
