// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mobiscanner.shared

import app.reseam.patch.klass
import app.reseam.patch.method

// The manifest's component factory is created first and starts Pairip from its class initializer.
internal val pairipBootstrap = klass("androidx.core.app.CoreComponentFactory").method("<clinit>")

internal val loadPairipLibrary =
    method("loadPairipLibrary") {
        name("<clinit>")
        strings("pairipcore")
    }
