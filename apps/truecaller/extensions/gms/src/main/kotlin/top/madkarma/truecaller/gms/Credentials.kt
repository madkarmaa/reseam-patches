// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Credentials") @file:Suppress("unused")

package top.madkarma.truecaller.gms

import android.util.Log
import app.reseam.runtime.Export

@Export
interface GoogleOption

@Export
interface CredentialRequest {
    @Export
    fun reseamOptions(): List<*>
}

@Export
fun isGoogleRequest(request: Any?): Boolean {
    val options = (request as? CredentialRequest)?.reseamOptions() ?: return false
    if (options.isEmpty() || !options.all { it is GoogleOption }) return false

    Log.i("TruecallerGms", "Found a Google sign-in request")
    return true
}

@Export
fun useModernCredentials(available: Boolean, request: Any?): Boolean =
    !isGoogleRequest(request) && available

@Export
interface CredentialProviderFactory {
    @Export
    fun reseamBundledProvider(): Any?

    @Export
    fun reseamDefaultProvider(request: Any?): Any?
}

@Export
fun selectProvider(factory: CredentialProviderFactory, request: Any?): Any? =
    if (isGoogleRequest(request)) factory.reseamBundledProvider()
    else factory.reseamDefaultProvider(request)
