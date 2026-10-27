// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.weather

import app.reseam.patch.after
import app.reseam.patch.patch
import app.reseam.patch.replace
import app.reseam.patch.replaceAllStrings
import top.madkarma.extensions.bindings.WeatherApi
import top.madkarma.extensions.bindings.WeatherApiKeyField
import top.madkarma.patches.niagara.pro.unlockPro
import top.madkarma.patches.shared.setStringEverywhere

val weatherWidgetFix = patch("Weather widget fix") {
    description(
        "Replaces the Niagara weather service with WeatherAPI.com. Enter your API key in the weather settings.",
    )
    compatibleWith("bitpit.launcher")

    // Niagara exposes the weather widget only to Pro users.
    dependsOn(unlockPro)

    execute {
        val hint = resources.setStringEverywhere(
            "weather_forecast_hint",
            "Weather forecasts are probabilistic and can be wrong. Never use them as the sole basis for safety-critical decisions.",
        )
        if (!hint) error("Weather widget fix: weather_forecast_hint string not writable")

        val provider = resources.setStringEverywhere("open_weather", "WeatherAPI.com")
        if (!provider) error("Weather widget fix: open_weather string not writable")

        val links = weatherSheet.replaceAllStrings(
            WEATHER_WIDGET_ARTICLE_URL,
            "https://www.weatherapi.com/terms.aspx",
        )
        if (links != 1) error("Weather widget fix: expected 1 help link, rewrote $links")

        log.info("Weather widget fix: ${weatherSheet.descriptor} links to the WeatherAPI.com terms.")

        weatherFetch.replace {
            val context = thisObject.field(weatherContext)
            val api = call(WeatherApi.create, context)
            val json = api.call(WeatherApi.fetch)
            val timestamp = api.call(WeatherApi.now)
            val forecast = thisObject.call(parseResponse, json, timestamp)

            thisObject.call(cacheResponse, json)
            thisObject.call(publishState, forecast)

            returnValue(forecast)
        }
        log.info("Weather widget fix: ${weatherFetch.descriptor} fetches and caches WeatherAPI.com forecasts.")

        weatherContentBind.after {
            call(WeatherApiKeyField.attach, lastParam)
        }
        log.info("Weather widget fix: ${weatherContentBind.descriptor} adds the API key field to weather settings.")
    }
}
