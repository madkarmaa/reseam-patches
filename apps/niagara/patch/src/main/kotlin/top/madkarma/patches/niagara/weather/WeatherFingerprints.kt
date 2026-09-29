package top.madkarma.patches.niagara.weather

import app.reseam.patch.ExtClass
import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

object WeatherApi : ExtClass("top.madkarma.extensions.WeatherApi") {
    val create = static("create", Type.Context, returns = "top.madkarma.extensions.WeatherApi")
    val fetch = method("fetch", returns = Type.String)
    val now = method("now", returns = Type.Long)
}

object ApiKeyField : ExtClass("top.madkarma.extensions.WeatherApiKeyField") {
    val attach = static("attach", Type.Object)
}

// The repository backing the weather widget and the forecast screen. Found
// through the strings only its fetch path loads; every string below was
// verified to occur in exactly one class/method (see smali).
val weatherRepo = klass("weather repository") {
    strings("Weather not fetched yet", "Cache cleared", "location missing")
}

// Suspend entry of the full fetch: resolves location and language, then
// runs the backend call. The only method in the repo touching the dynamic
// location flag, so the match is unique by construction.
val weatherFetch = method("weather fetch") {
    inClass(weatherRepo)
    strings("bitpit.launcher.key.WEATHER_LOCATION_IS_DYNAMIC")
}

// JSON -> forecast model plus fetch timestamp. Sole (String, long)
// method in the repo (return type left open: it is the obfuscated
// forecast model, so an exact descriptor would hardcode it).
val parseResponse = method("weather response parser") {
    inClass(weatherRepo)
    params(Type.String, Type.Long)
}

// Sole (String) -> void method in the repo: persists the raw JSON.
val cacheResponse = method("weather response cache") {
    inClass(weatherRepo)
    params(Type.String)
    returns(Type.Void)
}

// Sole (Object) -> void method in the repo: publishes the model to the UI.
val publishState = method("weather state publisher") {
    inClass(weatherRepo)
    params(Type.Object)
    returns(Type.Void)
}

const val WEATHER_WIDGET_ARTICLE_URL = "https://help.niagaralauncher.app/article/105-weather-widget"

// Builder of the weather settings sheet. Found through the help-article
// URL it embeds, which occurs exactly once in the app.
val weatherSheet = method("weather sheet") {
    strings(WEATHER_WIDGET_ARTICLE_URL)
}

// Content binder shared by several settings dialogs; its default branch
// inflates weather_dialog_content (location plus temperature-units
// buttons). Unique by the two location pref keys plus the view lookups,
// which the backend fetch method never performs.
val weatherContentBind = method("weather content binder") {
    strings(
        "bitpit.launcher.key.WEATHER_LOCATION_NAME",
        "bitpit.launcher.key.WEATHER_LOCATION_IS_DYNAMIC"
    )
    calls {
        owner("android.view.View")
        name("findViewById")
    }
}
