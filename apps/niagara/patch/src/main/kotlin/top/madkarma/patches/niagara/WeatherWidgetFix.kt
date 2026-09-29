@file:Suppress("unused")

package top.madkarma.patches.niagara

import app.reseam.patch.*

object WeatherApi : ExtClass("top.madkarma.extensions.WeatherApi") {
    val create =
        static("create", Type.Context, Type.String, returns = "top.madkarma.extensions.WeatherApi")
    val fetch = method("fetch", returns = Type.String)
    val now = method("now", returns = Type.Long)
}

// The repository backing the weather widget and the forecast screen. Found
// through the strings only its fetch path loads; every string below was
// verified to occur in exactly one class/method (see smali).
private val weatherRepo = klass("weather repository") {
    strings("Weather not fetched yet", "Cache cleared", "location missing")
}

// Suspend entry of the full fetch: resolves location and language, then
// runs the backend call. The only method in the repo touching the dynamic
// location flag, so the match is unique by construction.
private val weatherFetch = method("weather fetch") {
    inClass(weatherRepo)
    strings("bitpit.launcher.key.WEATHER_LOCATION_IS_DYNAMIC")
}

// JSON -> forecast model plus fetch timestamp. Sole (String, long)
// method in the repo (return type left open: it is the obfuscated
// forecast model, so an exact descriptor would hardcode it).
private val parseResponse = method("weather response parser") {
    inClass(weatherRepo)
    params(Type.String, Type.Long)
}

// Sole (String) -> void method in the repo: persists the raw JSON.
private val cacheResponse = method("weather response cache") {
    inClass(weatherRepo)
    params(Type.String)
    returns(Type.Void)
}

// Sole (Object) -> void method in the repo: publishes the model to the UI.
private val publishState = method("weather state publisher") {
    inClass(weatherRepo)
    params(Type.Object)
    returns(Type.Void)
}

private const val WEATHER_WIDGET_ARTICLE_URL =
    "https://help.niagaralauncher.app/article/105-weather-widget"

// Builder of the weather settings sheet. Found through the help-article
// URL it embeds, which occurs exactly once in the app.
private val weatherSheet = method("weather sheet") {
    strings(WEATHER_WIDGET_ARTICLE_URL)
}

val weatherWidgetFix = patch("Weather widget fix") {
    description(
        "Serves the weather widget from WeatherAPI.com instead of the Niagara backend.",
    )
    compatibleWith("bitpit.launcher")

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
