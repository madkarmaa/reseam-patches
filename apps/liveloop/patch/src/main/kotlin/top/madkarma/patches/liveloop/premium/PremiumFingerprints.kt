// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.liveloop.premium

import app.reseam.patch.Type
import app.reseam.patch.method

// Every pro_status writer (server check, Play restore, purchases, sign-out) goes through the
// app's only (String, boolean) wrapper around SharedPreferences.Editor.putBoolean.
internal val putPreferenceBoolean =
    method("putPreferenceBoolean") {
        calls {
            owner($$"Landroid/content/SharedPreferences$Editor;")
            name("putBoolean")
        }
        params(Type.String, Type.Boolean)
        returns(Type.Void)
    }
