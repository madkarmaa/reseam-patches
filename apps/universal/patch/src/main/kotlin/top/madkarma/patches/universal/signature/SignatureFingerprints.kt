// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.universal.signature

import app.reseam.patch.MethodTarget
import app.reseam.patch.addMethod
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.dex.isSet
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methodTarget

internal val pairipStartup = klass("com.pairip.StartupLauncher").method("launch")

private const val ATTACH_BASE_CONTEXT = "attachBaseContext"
private const val ATTACH_BASE_CONTEXT_PROTO = "(Landroid/content/Context;)V"

internal fun signatureAttachContext(): MethodTarget =
    methodTarget("signatureAttachContext") {
        val applicationName =
            manifest.applicationClass ?: error("SignatureKiller: manifest has no Application class")

        val application = klass(applicationName)

        application.classDef.method(ATTACH_BASE_CONTEXT, ATTACH_BASE_CONTEXT_PROTO)?.let {
            log.info("SignatureKiller: $applicationName declares attachBaseContext.")
            return@methodTarget it
        }

        val inherited =
            application.classDef.superclassChain.firstNotNullOfOrNull {
                it.method(ATTACH_BASE_CONTEXT, ATTACH_BASE_CONTEXT_PROTO)
            }
        if (inherited != null && AccessFlags.FINAL.isSet(inherited.info.accessFlags)) {
            log.info(
                "SignatureKiller: $applicationName inherits a final attachBaseContext from ${inherited.owner}.",
            )
            return@methodTarget inherited
        }

        val superclass =
            application.classDef.superclass
                ?: error("SignatureKiller: Application has no superclass")
        log.info(
            "SignatureKiller: $applicationName adds an attachBaseContext override that calls $superclass first.",
        )

        // The DSL hooks existing methods; an inherited platform method needs a forwarding override
        // first.
        application
            .addMethod(ATTACH_BASE_CONTEXT, ATTACH_BASE_CONTEXT_PROTO, AccessFlags.PROTECTED) {
                thisObject.callSuper(
                    superclass,
                    ATTACH_BASE_CONTEXT,
                    ATTACH_BASE_CONTEXT_PROTO,
                    param(0),
                )
                returnVoid()
            }
            .method
    }
