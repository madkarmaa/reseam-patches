// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.inshot.ai

import app.reseam.patch.Type
import app.reseam.patch.method

// Remote config stores whether each cloud AI tool is available in these preferences, and the
// tools' managers, menus, and promotions read them only through these getters. The remote-config
// handlers write the same keys but return nothing.
private fun cloudToolSwitch(key: String) =
    method(key) {
        strings(key)
        returns(Type.Boolean)
    }

internal val cloudToolSwitches =
    listOf(
        cloudToolSwitch("isSupportAutoCut"), // AI Cut
        cloudToolSwitch("isSupportCaption"), // Auto captions
        cloudToolSwitch("isSupportTTS"), // AI Speech
        cloudToolSwitch("isSupportPhotoEnhance"),
        cloudToolSwitch("isSupportVideoEnhance"),
    )
