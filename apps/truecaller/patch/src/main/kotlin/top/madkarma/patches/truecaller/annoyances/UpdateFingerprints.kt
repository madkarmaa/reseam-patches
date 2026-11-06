// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.annoyances

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val updateType = klass("com.truecaller.forcedupdate.UpdateType")

internal val currentUpdateType =
    method("currentUpdateType") {
        strings("forcedUpdate_updateType")
        returns(updateType.descriptor)
    }

internal val inAppUpdateConfiguration =
    method("inAppUpdateConfiguration") {
        strings("InAppUpgradeConfig_49679")
        returns(Type.Object)
    }
