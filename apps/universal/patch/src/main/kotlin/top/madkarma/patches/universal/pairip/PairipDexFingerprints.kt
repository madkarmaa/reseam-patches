// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.universal.pairip

import app.reseam.patch.klass
import app.reseam.patch.method

internal val pairipStartup = klass("com.pairip.StartupLauncher").method("launch")
internal val pairipContext = klass("com.pairip.VMRunner").method("getContext")

internal fun liftedStub(body: LiftedMethod) =
    method("Pairip stub for ${body.owner}->${body.name}") {
        inClass(klass(body.receiver))
        name(body.name)
        params(*body.parameters.drop(1).toTypedArray())
        returns(body.returnType)
        calls {
            owner("java.lang.reflect.Method")
            name("invoke")
        }
    }
