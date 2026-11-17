// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mtmanager.vip

import app.reseam.patch.*
import app.reseam.patch.dex.AccessFlags

private val request = klass("networkRequest") { strings("Request{method=") }
private val response = klass("networkResponse") { strings("Response{protocol=") }
private val validateChain = method("validateNetworkChain") { strings(" returned null") }

private val chain =
    classTarget("networkChain") {
        bytecode.findClass(validateChain.owner) ?: error("Network chain class is missing")
    }

internal val chainRequest =
    chain
        .methods {
            params()
            returns(request.descriptor)
        }
        .single()

internal val chainProceed =
    chain
        .methods {
            params(request.descriptor)
            returns(response.descriptor)
        }
        .single()

// These interceptors replace successful responses with an empty, client-generated 403.
internal val blockedNetworkResponses =
    methods("blockedNetworkResponses") {
        hasParam(chain.descriptor)
        returns(response.descriptor)
        literals(403)
    }

private val networkStorageClosers =
    methods("networkStorageClosers") {
        strings("net")
        flags(AccessFlags.STATIC)
    }

// An account-state change closes VIP-only network storage unless the new state is a VIP state.
// Its handler is the only static "net" method that receives the account-state enum.
private val accountStateChange =
    methodTarget("accountStateChange") {
        networkStorageClosers.all
            .singleOrNull { closer ->
                closer.parameterTypes.any {
                    bytecode.findClass(it)?.superclass == "Ljava/lang/Enum;"
                }
            }
            ?.method ?: error("Expected one account-state network storage handler")
    }

internal val vipAccountState =
    method("vipAccountState") {
        calledBy(accountStateChange)
        params()
        returns(Type.Boolean)
    }
