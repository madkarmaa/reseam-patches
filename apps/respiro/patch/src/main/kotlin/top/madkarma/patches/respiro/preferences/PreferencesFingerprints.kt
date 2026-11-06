// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.respiro.preferences

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val preferences =
    klass("preferences") {
        strings("Do mutate preferences once returned to DataStore.")
    }

internal val readPreference =
    method("readPreference") {
        inClass(preferences)
        returns(Type.Object)
    }
