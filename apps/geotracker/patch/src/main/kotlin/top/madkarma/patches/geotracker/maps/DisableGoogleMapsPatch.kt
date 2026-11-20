// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.geotracker.maps

import app.reseam.patch.*
import top.madkarma.geotracker.maps.bindings.MapProviders
import top.madkarma.patches.geotracker.GeoTrackerPatchStrings

val disableGoogleMaps =
    patch(GeoTrackerPatchStrings.disableGoogleMapsName) {
        description(GeoTrackerPatchStrings.disableGoogleMapsDescription)
        compatibleWith("com.ilyabogdanovich.geotracker")

        execute {
            // Google Maps rejects the app's API key once the app is re-signed, leaving blank tiles.
            val provider = klass(storedMapProvider.returnType)
            val google = provider.enumConstant("GOOGLE")
            val mapbox = provider.enumConstant("MAPBOX")

            mapProvidersInitializer.after {
                setStatic(
                    mapProviders,
                    call(
                        MapProviders.withoutGoogle,
                        staticField(mapProviders),
                        staticField(google),
                    ),
                )
            }
            log.info(
                "Maps: ${mapProvidersInitializer.descriptor} offers every map provider except Google Maps.",
            )

            storedMapProvider.after {
                result.assign(
                    call(
                            MapProviders.replaceGoogle,
                            result,
                            staticField(google),
                            staticField(mapbox),
                        )
                        .cast(storedMapProvider.returnType),
                )
            }
            log.info(
                "Maps: ${storedMapProvider.descriptor} uses OSM/Mapbox by default and in place of Google Maps.",
            )
        }
    }
