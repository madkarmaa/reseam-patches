// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.assttouch.startup

import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

private val mainActivity = klass("com.utae.assttouch.activity.MainActivity")

internal val mainActivityCreate = mainActivity.method("onCreate")

// Compose's snapshot MutableState implementation.
private val mutableState = klass("mutableState") { strings("MutableState(value=") }

internal val setStateValue = mutableState.method("setValue")

// Whether the launch intro has played: onCreate seeds it with "restored from a saved state", the
// intro animation sets it when done, and the launch gate waits for it before showing the app.
internal val introPlayed =
    mainActivityCreate.field("introPlayed") {
        owner(mainActivity.descriptor)
        type(mutableState.descriptor)
    }
