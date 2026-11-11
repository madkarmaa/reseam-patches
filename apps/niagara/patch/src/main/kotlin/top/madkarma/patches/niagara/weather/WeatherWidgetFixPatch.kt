// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.niagara.weather

import app.reseam.patch.className
import app.reseam.patch.patch
import app.reseam.patch.replace
import app.reseam.patch.settings.section
import top.madkarma.extensions.bindings.WeatherRepository
import top.madkarma.niagara.settings.NiagaraStrings
import top.madkarma.niagara.settings.WeatherSettings
import top.madkarma.patches.niagara.NiagaraPatchStrings
import top.madkarma.patches.niagara.pro.unlockPro
import top.madkarma.patches.niagara.settings.niagaraSettings
import top.madkarma.patches.shared.setStringEverywhere

val weatherWidgetFix =
    patch(NiagaraPatchStrings.weatherWidgetFixName) {
        description(
            NiagaraPatchStrings.weatherWidgetFixDescription,
        )
        compatibleWith("bitpit.launcher")

        // Niagara exposes the weather widget only to Pro users.
        dependsOn(unlockPro)

        settings(
            niagaraSettings,
            section(NiagaraStrings.sectionWeather, WeatherSettings.weatherApiKey),
        )

        execute {
            val hint =
                resources.setStringEverywhere(
                    "weather_forecast_hint",
                    "Weather forecasts are probabilistic and can be wrong. Never use them as the sole basis for safety-critical decisions.",
                )
            if (!hint) error("Weather widget fix: weather_forecast_hint string not writable")

            val provider = resources.setStringEverywhere("open_weather", "WeatherAPI.com")
            if (!provider) error("Weather widget fix: open_weather string not writable")

            val links =
                weatherSheet.method.replaceAllStrings(
                    WEATHER_WIDGET_ARTICLE_URL,
                    "https://www.weatherapi.com/terms.aspx",
                )
            if (links != 1) error("Weather widget fix: expected 1 help link, rewrote $links")

            log.info(
                "Weather widget fix: ${weatherSheet.descriptor} links to the WeatherAPI.com terms.",
            )

            val hasResponseParser = responseParsers.all.isNotEmpty()

            val parserName = if (hasResponseParser) parseResponse.name else ""

            val readerClassName =
                if (hasResponseParser) "" else className(cachedResponseConstructor.owner)

            val readerMethodName = if (hasResponseParser) "" else readCachedResponse.name
            val readerResultFieldName = if (hasResponseParser) "" else cachedResultValue.name

            weatherFetch.replace {
                val repository =
                    if (weatherFetch.method.isStatic) paramOfType(weatherContext.owner)
                    else thisObject

                returnValue(
                    call(
                        WeatherRepository.fetchForecast,
                        repository,
                        repository.field(weatherContext),
                        string(parserName),
                        string(cacheResponse.name),
                        string(publishState.name),
                        string(readerClassName),
                        string(readerMethodName),
                        string(readerResultFieldName),
                    ),
                )
            }
            log.info(
                "Weather widget fix: ${weatherFetch.descriptor} fetches and caches WeatherAPI.com forecasts.",
            )
        }
    }
