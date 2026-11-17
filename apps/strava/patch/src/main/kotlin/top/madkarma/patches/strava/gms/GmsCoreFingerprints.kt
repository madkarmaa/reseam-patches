// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.strava.gms

import app.reseam.patch.Type
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

private const val CONNECT_DIAGNOSTIC =
    "Calling connect() while still connected, missing disconnect() for "

// This build renames the client's members, so its diagnostics identify them instead.
private val connectService = method("connectService") { strings(CONNECT_DIAGNOSTIC) }

private val serviceClient = klass(connectService.owner)

private val endpointPackageName =
    method("endpointPackageName") { strings("Failed to connect when checking package") }

private val serviceConfiguration = endpointPackageName.field { owner(serviceClient.descriptor) }

internal val serviceConfigurationConstructor =
    method("serviceConfigurationConstructor") {
        inClass(klass(serviceConfiguration.type))
        name("<init>")
    }

// Connecting asks the client for its service action, the only abstract String getter it calls.
internal val serviceAction =
    method("serviceAction") {
        calledBy(connectService)
        returns(Type.String)
        flags(AccessFlags.ABSTRACT)
    }

// Play Services' availability cache skips its check for clients that don't require Play Services.
private val playServicesAvailability =
    method("playServicesAvailability") {
        calls { name("isGooglePlayServicesAvailable") }
        calls {
            owner("android.util.SparseIntArray")
            name("put")
        }
    }

// The cache asks through the client interface, which the client implements under the same name.
private val clientRequiresPlayServices =
    method("clientRequiresPlayServices") {
        calledBy(playServicesAvailability)
        returns(Type.Boolean)
    }

internal val requiresPlayServices =
    method("requiresPlayServices") {
        inClass(serviceClient)
        name(clientRequiresPlayServices.name)
        returns(Type.Boolean)
    }

// All occurrences in this APK describe Google account types, rather than class/package names.
internal val googleAccountTypes = methods("googleAccountTypes") { strings("com.google") }
