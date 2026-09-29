@file:Suppress("unused")

package top.madkarma.patches.niagara.weather

import app.reseam.patch.*
import top.madkarma.patches.niagara.pro.unlockPro

val weatherWidgetFix = patch("Weather widget fix") {
    description(
        "Serves the weather widget from WeatherAPI.com instead of the Niagara backend.",
    )
    compatibleWith("bitpit.launcher")

    // weather widget is only available on Pro, but Pro patch doesn't require this patch to function
    // unless you actually use the weather widget
    dependsOn(unlockPro)

    val apiKeyOption = stringOption(
        "weatherApiKey",
        title = "WeatherAPI.com key",
        description = "API key used for every weather fetch. Get yours at weatherapi.com.",
        required = true,
    )

    execute {
        val apiKey = options[apiKeyOption]
        if (apiKey.isBlank()) error("Weather widget fix: patch option weatherApiKey is blank")

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

        val appContext = weatherRepo.fieldOfType(Type.Context)
        weatherFetch.replace {
            val context = thisObject.field(appContext)
            val api = call(WeatherApi.create, context, string(apiKey))
            val json = api.call(WeatherApi.fetch)
            val timestamp = api.call(WeatherApi.now)
            val forecast = thisObject.call(parseResponse, json, timestamp)

            thisObject.call(cacheResponse, json)
            thisObject.call(publishState, forecast)

            returnValue(forecast)
        }

        log.info("Weather widget fix: fetch wired to WeatherAPI.com")
    }
}
