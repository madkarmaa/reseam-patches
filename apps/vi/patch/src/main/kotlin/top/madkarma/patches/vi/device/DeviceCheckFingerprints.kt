// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.vi.device

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

internal val originalApplication =
    klass("originalApplication") {
        strings("Failed to initialize SoLoader")
        extends(Type.Application)
    }

private val protectionLoader =
    method("protectionLoader") {
        strings("cachehandler")
    }

internal val protectionStartup =
    method("protectionStartup") {
        name("attachBaseContext")
        calls(protectionLoader)
    }
