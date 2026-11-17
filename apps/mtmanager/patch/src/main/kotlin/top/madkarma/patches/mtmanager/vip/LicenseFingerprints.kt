// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mtmanager.vip

import app.reseam.patch.*

// The local license and the account each keep their values in a static map that also works as an
// IntFunction. The license map is keyed by int, the account map by char.
private val licenseFunction =
    klass("licenseFunction") {
        implements("java.util.function.IntFunction")
        custom { methods.any { it.name == "get" && it.parameterTypes == listOf(Type.Int) } }
    }

private val accountFunction =
    klass("accountFunction") {
        implements("java.util.function.IntFunction")
        custom {
            methods.any { it.parameterTypes == listOf(Type.Char) && it.returnType == Type.Object }
        }
    }

private val licenseMap =
    klass("licenseMap") {
        implements(Type.Map)
        implements(licenseFunction.descriptor)
    }

private val accountMap =
    klass("accountMap") {
        implements(Type.Map)
        implements(accountFunction.descriptor)
    }

internal val licenseStartup =
    method("licenseStartup") {
        name("<clinit>")
        accesses { type(licenseMap.descriptor) }
    }

internal val accountStartup =
    method("accountStartup") {
        name("<clinit>")
        accesses { type(accountMap.descriptor) }
    }

internal val licenseState = licenseStartup.field { type(licenseMap.descriptor) }

internal val accountState = accountStartup.field { type(accountMap.descriptor) }

// Each native getter reads one license entry. The file opener's validity getter is one of them, but
// nothing in DEX calls either, so every one is covered.
internal val licenseValues =
    methods("licenseValues") {
        inClass(klass(licenseState.owner))
        params()
        returns(Type.Long)
    }

// The account screen turns the VIP expiry into whole days remaining.
private val vipDaysRemaining =
    method("vipDaysRemaining") {
        literals(86_400_000)
        calls {
            owner(vipEntitlement.owner)
            params()
            returns(Type.Long)
        }
    }

internal val vipExpiry =
    method("vipExpiry") {
        calledBy(vipDaysRemaining)
        inClass(klass(vipEntitlement.owner))
        returns(Type.Long)
    }
