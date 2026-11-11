// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.textra.shared

import app.reseam.patch.klass
import app.reseam.patch.method

internal val pairipBootstrap = klass("androidx.core.app.CoreComponentFactory").method("<clinit>")

internal val loadPairipLibrary =
    method("loadPairipLibrary") {
        name("<clinit>")
        strings("pairipcore")
    }
