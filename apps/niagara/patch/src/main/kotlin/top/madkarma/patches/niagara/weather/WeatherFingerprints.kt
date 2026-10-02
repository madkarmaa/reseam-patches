// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.niagara.weather

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

internal val weatherRepository = klass("weatherRepository") {
    strings("Weather not fetched yet")
}

internal val weatherFetch = method("weatherFetch") {
    strings("location missing")
}

// The parser has no strings; it is the only caller of the retained WeatherResponse serializer.
internal val parseResponse = method("parseResponse") {
    calls {
        owner("bitpit.launcher.backend.weather.model.WeatherResponse\$Companion")
        name("serializer")
    }
}

internal val cacheResponse = method("cacheResponse") {
    inClass(weatherRepository)
    strings("weather2")
}

// The publisher's refresh-interval string is shared; its single model argument distinguishes it.
internal val publishState = method("publishState") {
    strings("weather_refresh_interval_high")
    params(Type.Object)
}

internal const val WEATHER_WIDGET_ARTICLE_URL =
    "https://help.niagaralauncher.app/article/105-weather-widget"

internal val weatherSheet = method("weatherSheet") {
    strings(WEATHER_WIDGET_ARTICLE_URL)
}

// The other location-name readers only touch preferences; this one binds the settings views.
internal val weatherContentBind = method("weatherContentBind") {
    strings("bitpit.launcher.key.WEATHER_LOCATION_NAME")
    calls {
        owner("android.view.View")
        name("findViewById")
    }
}
