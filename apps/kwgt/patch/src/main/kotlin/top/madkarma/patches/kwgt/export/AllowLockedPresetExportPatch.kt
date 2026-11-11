// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kwgt.export

import app.reseam.patch.after
import app.reseam.patch.patch
import top.madkarma.patches.kwgt.KWGTPatchStrings

val allowLockedPresetExport =
    patch(KWGTPatchStrings.allowLockedPresetExportName) {
        description(KWGTPatchStrings.allowLockedPresetExportDescription)
        compatibleWith("org.kustom.widget")

        execute {
            // Bypass the export guard without changing how locked presets load or serialize.
            presetExportReadOnlyCheck.after { capture("isReadOnly").assign(bool(false)) }
            log.info(
                "Export: ${presetExportReadOnlyCheck.method.descriptor} allows exporting locked presets.",
            )
        }
    }
