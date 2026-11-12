// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.radarbot.shared

import app.reseam.patch.klass
import app.reseam.patch.method

// The manifest's component factory is created first and starts Pairip from its class initializer.
internal val pairipBootstrap = klass("androidx.core.app.CoreComponentFactory").method("<clinit>")

internal val loadPairipLibrary =
    method("loadPairipLibrary") {
        name("<clinit>")
        strings("pairipcore")
    }

// The parameters every request to Radarbot's servers starts from.
private val baseRequestParameters =
    method("baseRequestParameters") { strings("rb_version", "id_dispositivo") }

private val requestParameters = klass(baseRequestParameters.returnType)

internal val putRequestParameter =
    method("putRequestParameter") {
        inClass(requestParameters)
        params("Ljava/lang/String;", "Ljava/lang/String;")
    }
