// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.shared

import app.reseam.patch.*
import top.madkarma.truecaller.behavior.bindings.FeatureFlags

internal val featureFlagOverrides = patch {
    hidden()
    execute {
        val readers = readFeatureFlags.all
        if (readers.isEmpty()) error("No feature flag getter found")

        for (reader in readers) {
            reader.after { returnValue(FeatureFlags.enabled(paramOfType(Type.String), result)) }
            log.info("Feature flags: ${reader.descriptor} overrides feature flags by key.")
        }
    }
}

internal fun PatchRuntime.disableFeatureFlag(key: String) {
    val consumers = featureFlagConsumers(key)
    if (consumers.isEmpty()) error("No feature flag consumer found for $key")

    // Register before the first flag read, including reads before Application.onCreate.
    FeatureFlags.target.method("<clinit>") { includeExtensions() }.after {
        FeatureFlags.disable(string(key))
    }
    log.info("Feature flags: registered an override for $key, used by ${consumers.joinToString { it.descriptor }}.")
}
