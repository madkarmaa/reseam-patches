// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.weather

import app.reseam.patch.*
import top.madkarma.patches.niagara.WeatherApi
import top.madkarma.patches.niagara.WeatherApiKeyField
import top.madkarma.patches.niagara.pro.unlockPro

val weatherWidgetFix = patch("Weather widget fix") {
    description(
        "Serves the weather widget from WeatherAPI.com instead of the Niagara backend. Enter your key in the weather settings sheet.",
    )
    compatibleWith("bitpit.launcher")

    // Niagara exposes the weather widget only to Pro users.
    dependsOn(unlockPro)

    execute {
        val hint = resources.setString(
            "weather_forecast_hint",
            "Weather forecasts are probabilistic and can be wrong. Never use them as the sole basis for safety-critical decisions.",
        )
        if (!hint) error("Weather widget fix: weather_forecast_hint string not writable")

        val provider = resources.setString("open_weather", "WeatherAPI.com")
        if (!provider) error("Weather widget fix: open_weather string not writable")

        val links = weatherSheet.replaceAllStrings(
            WEATHER_WIDGET_ARTICLE_URL,
            "https://www.weatherapi.com/terms.aspx",
        )
        if (links != 1) error("Weather widget fix: expected 1 help link, rewrote $links")

        log.info("Weather widget fix: sheets updated")

        val appContext = weatherRepository.fieldOfType(Type.Context)
        weatherFetch.replace {
            val context = thisObject.field(appContext)
            val api = call(WeatherApi.create, context)
            val json = api.call(WeatherApi.fetch)
            val timestamp = api.call(WeatherApi.now)
            val forecast = thisObject.call(parseResponse, json, timestamp)

            thisObject.call(cacheResponse, json)
            thisObject.call(publishState, forecast)

            returnValue(forecast)
        }

        log.info("Weather widget fix: fetch wired to WeatherAPI.com")

        weatherContentBind.after {
            call(WeatherApiKeyField.attach, lastParam)
        }

        log.info("Weather widget fix: key field added to weather sheet")
    }
}
