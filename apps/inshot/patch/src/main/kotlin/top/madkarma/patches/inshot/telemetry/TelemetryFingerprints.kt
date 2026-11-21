// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.inshot.telemetry

import app.reseam.patch.Type
import app.reseam.patch.method

// Every app event goes through this sink, which packs the parallel key and value arrays into a
// Bundle and hands it to the Firebase Analytics wrapper.
internal val logEvent =
    method("logEvent") {
        strings("event")
        params(Type.String, "[${Type.Object}", "[${Type.Object}")
    }
