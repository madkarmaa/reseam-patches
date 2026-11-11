// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("PairipDex")
@file:Suppress("unused")

package top.madkarma.universal.pairip

import android.content.Context
import android.os.Process
import android.util.Log
import app.reseam.runtime.Export
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private object AppClasses

private var patchedClasses: ClassLoader? = null

@Export
@Synchronized
fun initialize(context: Context?) {
    if (patchedClasses != null) return
    patchedClasses =
        loadPatchedDex(
            checkNotNull(context) { "Pairip has no application context" },
            AppClasses::class.java.classLoader,
        )
}

@Export
fun resolveMethod(owner: String?, name: String?, prototype: String?, diagnostics: Boolean): Method {
    val descriptor = checkNotNull(owner)
    check(descriptor.startsWith('L') && descriptor.endsWith(';')) {
        "Invalid embedded DEX class: $descriptor"
    }

    val loader = checkNotNull(patchedClasses) { "Patched Pairip DEX has not been initialized" }
    val type = MethodType.fromMethodDescriptorString(checkNotNull(prototype), loader)

    val definition =
        Class.forName(
            descriptor.substring(1, descriptor.length - 1).replace('/', '.'),
            false,
            loader,
        )

    val method = definition.getDeclaredMethod(checkNotNull(name), *type.parameterArray())
    check(Modifier.isStatic(method.modifiers) && method.returnType == type.returnType()) {
        "Embedded DEX method changed: $descriptor->$name$prototype"
    }

    method.isAccessible = true

    if (diagnostics)
        Log.i(
            "PairipDex",
            "Using $descriptor->$name$prototype from ${definition.classLoader}",
        )

    return method
}

@Export
fun recordPatchedMethod(descriptor: String?) {
    Log.i(
        "PairipDex",
        "Executed Reseam-patched embedded DEX method $descriptor; uid=${Process.myUid()}",
    )
}
