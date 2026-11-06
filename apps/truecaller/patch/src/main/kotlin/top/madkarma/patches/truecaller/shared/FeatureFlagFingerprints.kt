// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.shared

import app.reseam.patch.Type
import app.reseam.patch.methods

private const val FEATURE_STATE = "com.truecaller.featuretoggles.FeatureState"

// Local, remote, and default stores share this getter contract.
internal val readFeatureFlags =
    methods("readFeatureFlags") {
        params(Type.String, FEATURE_STATE)
    }

internal fun featureFlagConsumers(key: String) =
    methods(key) {
            strings(key)
            calls { params(Type.String, FEATURE_STATE) }
        }
        .all
