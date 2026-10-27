// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.region

import app.reseam.patch.klass
import app.reseam.patch.method

private val geographicBlockConfiguration = klass("geographicBlockConfiguration") {
    strings("GeoBlockConfig(entranceBlocks=")
}

internal val geographicBlockLists = listOf(
    geographicBlockConfiguration.method("getCertBlocks"),
    geographicBlockConfiguration.method("getDockBlocks"),
    geographicBlockConfiguration.method("getEntranceBlocks"),
    geographicBlockConfiguration.method("getPageBlocks"),
    geographicBlockConfiguration.method("getSchemeBlocks"),
)

private val effectConfigurationInitializer = method("effectConfigurationInitializer") {
    strings("initDownloadableModelSupport")
}

internal val effectPlatform = method("effectPlatform") {
    calledBy(effectConfigurationInitializer)
    name("platform")
}

internal val initialEffectDeviceIdentifier = method("initialEffectDeviceIdentifier") {
    calledBy(effectConfigurationInitializer)
    name("deviceId")
}

internal val updatedEffectDeviceIdentifier = method("updatedEffectDeviceIdentifier") {
    inClass(klass(effectConfigurationInitializer.returnType))
    name("setDeviceId")
}
