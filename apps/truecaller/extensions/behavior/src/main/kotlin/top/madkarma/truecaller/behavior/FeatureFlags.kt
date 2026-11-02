// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("FeatureFlags") @file:Suppress("unused")

package top.madkarma.truecaller.behavior

import app.reseam.runtime.Export
import top.madkarma.truecaller.settings.TruecallerSettings
import java.util.concurrent.ConcurrentHashMap

private val disabledKeys = ConcurrentHashMap.newKeySet<String>()

@Export
fun disable(key: String) {
    disabledKeys.add(key)
}

@Export
fun enabled(key: String?, original: Boolean): Boolean {
    if (key == null || key !in disabledKeys) return original
    return when (key) {
        "featureAutoAppLaunchPassive" -> original && !TruecallerSettings.disableAutoLaunch.value
        "featurePromptWearApp" -> original && !TruecallerSettings.hideWearSettings.value
        else -> false
    }
}
