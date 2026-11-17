// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.picsart.gms

import app.reseam.patch.*
import app.reseam.patch.dex.AccessFlags

// Builds that keep the Play services client names pass the destination package to its
// configuration.
private val serviceClient = klass("com.google.android.gms.common.internal.BaseGmsClient")

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

// Later builds rename the client, which its connect() diagnostic still identifies.
private val connectService =
    method("connectService") {
        strings("Calling connect() while still connected, missing disconnect() for ")
    }

// Their binding configuration fixes the Play services package itself instead of taking it.
internal val bindingConfigurationConstructor =
    method("bindingConfigurationConstructor") {
        name("<init>")
        calledBy(connectService)
        strings("com.google.android.gms")
    }

// Resolved lazily, since builds that keep the client names lack this constructor.
private val bindingConfiguration =
    classTarget("bindingConfiguration") {
        bytecode.findClass(bindingConfigurationConstructor.owner)
            ?: error("Binding configuration class is missing")
    }

// The configuration describes itself by its service action.
internal val bindingAction = bindingConfiguration.method("toString").field { type(Type.String) }

// The supervisor's bind timeout names the stalled service by its package.
internal val bindingPackage =
    method("bindTimeout") { strings("Timeout waiting for ServiceConnection callback ") }
        .field { owner(bindingConfiguration.descriptor) }

internal val bindingDynamicLookup = bindingConfiguration.fieldOfType(Type.Boolean)

// Connecting asks the client for its service action, the only abstract String getter it calls.
internal val renamedServiceAction =
    method("renamedServiceAction") {
        calledBy(connectService)
        returns(Type.String)
        flags(AccessFlags.ABSTRACT)
    }

internal val renamedRequiresPlayServices =
    method("renamedRequiresPlayServices") {
        inClass(klass(connectService.owner))
        name("requiresGooglePlayServices")
    }

// Analytics also loads "com.google" as a package prefix; account types build Accounts.
internal val googleAccountTypes =
    methods("googleAccountTypes") {
        strings("com.google")
        calls {
            owner("android.accounts.Account")
            name("<init>")
        }
    }
