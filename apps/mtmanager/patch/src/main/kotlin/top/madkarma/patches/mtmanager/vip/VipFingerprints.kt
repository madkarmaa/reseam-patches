// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mtmanager.vip

import app.reseam.patch.*
import app.reseam.patch.dex.*

private val application =
    classTarget("mtApplication") {
        val name = manifest.applicationClass ?: error("Application class is missing")
        bytecode.findClass(descriptor(name)) ?: error("Application class is not in the APK")
    }

internal val applicationStartup = application.method("onCreate")

private val completionSession =
    method("completionSession") {
        hasParam("java.lang.CharSequence")
        accesses {
            owner("android.content.pm.ApplicationInfo")
            name("uid")
        }
    }

internal val completionIdentity = completionSession.field { type("java.lang.Integer") }

// The UID guard contains exactly one 32-bit constant: its completion mask.
internal val completionIdentityMask: Int
    get() =
        completionSession.method.instructions
            .singleOrNull { it.opcode in setOf(Opcode.CONST, Opcode.CONST_HIGH16) }
            ?.literal
            ?.toInt() ?: error("Expected one completion mask in ${completionSession.descriptor}")

private val networkStorageMenu =
    method("networkStorageMenu") {
        strings("FTPS (VIP)", "SFTP (VIP)")
    }

// This menu calls one boolean method: the shared native VIP entitlement check.
internal val vipEntitlement =
    method("vipEntitlement") {
        calledBy(networkStorageMenu)
        returns(Type.Boolean)
    }
