// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kwgt.export

import app.reseam.patch.Type
import app.reseam.patch.method
import app.reseam.patch.point

private val exportPreset =
    method("exportPreset") {
        strings("org.kustom.DIALOG_PRESET_EXPORT", "Exporting a readonly preset!")
    }

private val isPresetReadOnly =
    method("isPresetReadOnly") {
        calledBy(exportPreset)
        strings("internal_readonly")
        returns(Type.Boolean)
    }

// The editor and the preset serializer also ask whether a preset is read-only and keep it locked,
// so only the export dialog's answer is replaced.
internal val presetExportReadOnlyCheck =
    exportPreset
        .point { calls(isPresetReadOnly) }
        .next { resultOf(Type.Boolean) }
        .captureAs("isReadOnly")
