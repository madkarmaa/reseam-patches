// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.geotracker.maps

import app.reseam.patch.*

private val displaySettings =
    klass("displaySettings") {
        strings("DisplaySettings(mapProvider=")
    }

// Reads the map provider setting, falling back to the display settings' default provider.
private val mapProviderSetting =
    method("mapProviderSetting") {
        strings("settings.display.map_provider")
        params()
        accesses { owner(displaySettings.descriptor) }
    }

// Parses the stored map provider. The setting above and the map's preference listener both read
// it through this parser.
internal val storedMapProvider =
    method("storedMapProvider") {
        calledBy(mapProviderSetting)
        returns(mapProviderSetting.returnType)
    }

private val mapConfigViewState =
    klass("mapConfigViewState") {
        strings("TripsMapConfigViewState(mapProviderSelection=")
    }

// The map configuration sheet offers every provider in this list.
internal val mapProviders =
    mapConfigViewState.method("<clinit>").field("mapProviders") { type("java.util.List") }

internal val mapProvidersInitializer =
    method("mapProvidersInitializer") {
        inClass(klass(mapProviders.owner))
        name("<clinit>")
    }
