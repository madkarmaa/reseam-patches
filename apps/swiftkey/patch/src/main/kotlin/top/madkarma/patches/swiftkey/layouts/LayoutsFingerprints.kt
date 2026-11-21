// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.swiftkey.layouts

import app.reseam.patch.klass
import app.reseam.patch.method

private val layoutSettings = klass("layoutSettings") { strings("_layout_for_hwr_quick_switch") }

// Names the layouts offered for a language pack. Extended Latin layouts, such as BEPO or
// GERMAN_NEO2, are offered to every Latin-script language, but only when an installed language pack
// lists them.
internal val listLayoutNames =
    method("listLayoutNames") {
        inClass(layoutSettings)
        returns("Ljava/util/HashMap;")
    }
