// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.googlephotos.maps

import app.reseam.patch.after
import app.reseam.patch.patch
import app.reseam.patch.settings.section
import top.madkarma.googlephotos.maps.bindings.MapTiles
import top.madkarma.googlephotos.settings.GooglePhotosSettings
import top.madkarma.googlephotos.settings.GooglePhotosStrings
import top.madkarma.patches.googlephotos.GOOGLE_PHOTOS
import top.madkarma.patches.googlephotos.GooglePhotosPatchStrings
import top.madkarma.patches.googlephotos.gms.gmsCoreSupport
import top.madkarma.patches.googlephotos.settings.googlePhotosSettings

val placesMapFix =
    patch(GooglePhotosPatchStrings.placesMapFixName) {
        description(
            GooglePhotosPatchStrings.placesMapFixDescription,
        )
        compatibleWith(GOOGLE_PHOTOS)
        dependsOn(gmsCoreSupport)

        settings(
            googlePhotosSettings,
            section(GooglePhotosStrings.sectionMaps, GooglePhotosSettings.mapboxAccessToken),
        )

        execute {
            mapsModuleContext.after { call(MapTiles.rememberMapsContext, result) }
            log.info(
                "Places map fix: ${mapsModuleContext.descriptor} remembers GmsCore's map renderer.",
            )

            mapLifecycleCallback.after { call(MapTiles.applyAccessToken) }
            log.info(
                "Places map fix: ${mapLifecycleCallback.descriptor} gives each new map a Mapbox key.",
            )
        }
    }
