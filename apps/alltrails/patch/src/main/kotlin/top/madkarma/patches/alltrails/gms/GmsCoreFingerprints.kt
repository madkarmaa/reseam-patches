// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.alltrails.gms

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

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
