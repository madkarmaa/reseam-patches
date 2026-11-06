// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.protonmail.accounts

import app.reseam.patch.method

internal val rustLibraryLoad =
    method("rustLibraryLoad") {
        strings("mail_uniffi")
        calls {
            owner("java.lang.System")
            name("loadLibrary")
        }
    }
