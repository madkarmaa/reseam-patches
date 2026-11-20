// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.timberradio.stations

import app.reseam.patch.klass
import app.reseam.patch.method

// The Timber catalog queries; the extension maps SEARCH and STATIONS_BY_COUNTRY by name.
internal val stationQueryType =
    klass("stationQueryType") { enumConstants("SEARCH", "STATIONS_BY_COUNTRY") }

// Requests one page of catalog stations and passes them to a callback. The server-data download
// logs the same text but takes the sync type instead.
internal val searchStation =
    method("searchStation") {
        strings("query ID")
        hasParam(stationQueryType.descriptor)
    }

// The extension builds and reads these app models through stubs, so their members must keep the
// stubbed signatures. The app's own classes are not obfuscated.
internal val stationModel = klass("com.msoft.timberradio.model.Station")
internal val streamModel = klass("com.msoft.timberradio.db.room.Stream")
internal val successResult = klass("com.msoft.timberradio.network.NetworkResult\$Success")
