// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.universal.signature

import app.reseam.patch.MethodTarget
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.buildInstructions
import app.reseam.patch.dex.isSet
import app.reseam.patch.klass
import app.reseam.patch.methodTarget
import app.reseam.patch.types.NewMethod

internal fun signatureAttachContext(): MethodTarget = methodTarget("signatureAttachContext") {
    val applicationName =
        manifest.applicationClass ?: error("SignatureKiller: manifest has no Application class")

    val application = klass(applicationName).classDef

    val prototype = "(Landroid/content/Context;)V"

    application.method("attachBaseContext", prototype)?.let { return@methodTarget it }

    val inherited = application.superclassChain.firstNotNullOfOrNull {
        it.method(
            "attachBaseContext", prototype
        )
    }
    if (inherited != null && AccessFlags.FINAL.isSet(inherited.info.accessFlags)) return@methodTarget inherited

    val superclass =
        application.superclass ?: error("SignatureKiller: Application has no superclass")

    // The DSL hooks existing methods; an inherited platform method needs a forwarding override first.
    application.addMethod(
        NewMethod(
            name = "attachBaseContext",
            proto = prototype,
            accessFlags = AccessFlags.PROTECTED.toUInt(),
            registersSize = 2u,
            insSize = 2u,
            outsSize = 2u,
            instructions = buildInstructions {
                invokeSuper(superclass, "attachBaseContext", prototype, 0, 1)
                returnVoid()
            },
            tries = emptyList(),
            catchHandlers = emptyList()
        )
    )
}
