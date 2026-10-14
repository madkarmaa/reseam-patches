// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.extensions

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import app.reseam.runtime.Export
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.HttpsURLConnection

/** Supplies Niagara's weather response through its existing parser, cache and UI. */
class WeatherApi private constructor(context: Context) {
    private val appContext = context.applicationContext

    @Export
    fun now(): Long = System.currentTimeMillis()

    // The worker continues after the caller's grace period and caches successful responses.
    @Export
    fun fetch(): String? {
        freshCache()?.let {
            return it
        }

        val worker = startWorker()
        try {
            worker.join(FETCH_GRACE_MS.toLong())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        if (!worker.finished()) throw buildNoWeather("Refreshing weather...", null)

        worker.failure?.let { throw asNoWeather(it) }
        return worker.json
    }

    private fun startWorker(): FetchWorker = synchronized(fetchLock) {
        val current = inFlight
        if (current != null && current.isAlive) return@synchronized current

        FetchWorker().also {
            inFlight = it
            it.start()
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun freshCache(): String? {
        val preferences = prefs()

        val at = preferences.getLong(KEY_CACHE_TIME, 0)
        if (at <= 0 || now() - at > CACHE_TTL_MS) return null

        val json = preferences.getString(KEY_CACHE_JSON, null)
        if (json.isNullOrEmpty()) return null

        return json
    }

    @SuppressWarnings("UseKtx")
    private fun storeCache(json: String) {
        prefs().edit().putString(KEY_CACHE_JSON, json).putLong(KEY_CACHE_TIME, now()).apply()
    }

    private fun asNoWeather(failure: Throwable): Throwable {
        val message = failure.message?.takeIf { it.isNotEmpty() } ?: "Failed to read weather data"
        return buildNoWeather(message, failure as? Exception)
    }

    private fun buildNoWeather(message: String, cause: Exception?): Throwable {
        try {
            val type = Class.forName("bitpit.launcher.weather.NoWeatherDataException")
            val constructor = type.getConstructor(
                Int::class.javaPrimitiveType,
                Exception::class.java,
                String::class.java,
            )
            return constructor.newInstance(6, cause, message) as Throwable
        } catch (exception: Exception) {
            return RuntimeException(message, cause ?: exception)
        }
    }

    private fun resolveCoords(): DoubleArray {
        val preferences = prefs()

        if (!preferences.getBoolean(
                KEY_DYNAMIC,
                false,
            ) && preferences.contains(KEY_LAT) && preferences.contains(KEY_LONG)
        ) return doubleArrayOf(
            preferences.getFloat(KEY_LAT, 0f).toDouble(),
            preferences.getFloat(KEY_LONG, 0f).toDouble(),
        )

        val location = lastKnown() ?: throw RuntimeException(
            "Location unavailable - allow location access or set a fixed location"
        )

        return doubleArrayOf(location.latitude, location.longitude)
    }

    @Suppress("MissingPermission")
    private fun lastKnown(): Location? {
        if (Build.VERSION.SDK_INT >= 23 && appContext.checkSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null

        val manager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        var best: Location? = null

        for (provider in arrayOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )) {
            val candidate = try {
                manager.getLastKnownLocation(provider)
            } catch (exception: IllegalArgumentException) {
                continue
            } catch (exception: SecurityException) {
                continue
            } ?: continue

            if (best == null || candidate.time > best.time) best = candidate
        }

        return best
    }

    private fun readLang(): String = prefs().getString(KEY_LANG, null)?.takeIf { it.isNotEmpty() }
        ?: Locale.getDefault().language

    private fun mapLang(lang: String?): String {
        for (row in LANG_MAP) if (row.lang == lang) return row.result
        return "en"
    }

    private fun download(latitude: Double, longitude: Double, language: String): String {
        var connection: HttpsURLConnection? = null
        try {
            connection = URL(
                requestUrl(
                    latitude,
                    longitude,
                    language,
                )
            ).openConnection() as HttpsURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val status = connection.responseCode

            val stream = if (status == 200) connection.inputStream else connection.errorStream
            val body = stream.readBytes().toString(Charsets.UTF_8)

            if (status != 200) throw apiError(parseError(body), null)
            return body
        } catch (exception: RuntimeException) {
            throw exception
        } catch (exception: Exception) {
            throw RuntimeException("Failed to reach weather service", exception)
        } finally {
            connection?.disconnect()
        }
    }

    private fun requestUrl(latitude: Double, longitude: Double, language: String): String =
        "$ENDPOINT?key=${currentApiKey()}&q=$latitude,$longitude&days=$FORECAST_DAYS&aqi=no&alerts=no&lang=$language"

    fun currentApiKey(): String =
        prefs().getString(KEY_API_RUNTIME, null)?.takeIf { it.isNotEmpty() } ?: ""

    @SuppressWarnings("UseKtx")
    fun saveApiKey(key: String?) {
        val value = key?.trim { it <= ' ' } ?: ""
        prefs().edit().putString(KEY_API_RUNTIME, value).remove(KEY_CACHE_JSON)
            .remove(KEY_CACHE_TIME).apply()
    }

    private fun parseError(body: String): JSONObject? {
        try {
            val root = JSONObject(body)
            if (root.has("error")) return root.getJSONObject("error")
        } catch (exception: Exception) {
            // Use the generic error message.
        }
        return null
    }

    private fun translate(
        root: JSONObject,
        latitude: Double,
        longitude: Double,
        language: String,
    ): JSONObject {
        val location = root.getJSONObject("location")
        val days = root.getJSONObject("forecast").getJSONArray("forecastday")
        val zoneId = location.optString("tz_id", "UTC")

        val forecast = JSONObject()
        forecast.put("current", translateCurrent(root.getJSONObject("current")))
        forecast.put("minutely", JSONArray())
        forecast.put("hourly", translateHourly(days))
        forecast.put("daily", translateDaily(days, zoneId))

        val envelope = JSONObject()
        envelope.put("timestamp", System.currentTimeMillis())
        envelope.put("provider", "WeatherAPI.com")
        envelope.put("lat", latitude.toString())
        envelope.put("long", longitude.toString())
        envelope.put("lang", language)
        envelope.put("forecast", forecast)

        return envelope
    }

    private fun translateHourly(days: JSONArray): JSONArray {
        val hourly = JSONArray()
        for (day in 0 until days.length()) {
            val hours = days.getJSONObject(day).getJSONArray("hour")
            for (hour in 0 until hours.length()) hourly.put(
                translateHour(hours.getJSONObject(hour))
            )
        }
        return hourly
    }

    private fun translateDaily(days: JSONArray, zoneId: String): JSONArray {
        val daily = JSONArray()
        for (day in 0 until days.length()) daily.put(translateDay(days.getJSONObject(day), zoneId))
        return daily
    }

    private fun translateCurrent(current: JSONObject): JSONObject {
        val point = JSONObject()
        point.put("dt", current.getLong("last_updated_epoch"))
        // Java widened each rounded float to double when selecting JSONObject.put.
        point.put("temp", current.getDouble("temp_c").toFloat().toDouble())
        point.put("feels_like", current.getDouble("feelslike_c").toFloat().toDouble())
        point.put(
            "weather",
            conditionArray(current.getJSONObject("condition"), current.optInt("is_day", 1) == 1),
        )
        return point
    }

    private fun translateHour(hour: JSONObject): JSONObject {
        val point = JSONObject()
        point.put("dt", hour.getLong("time_epoch"))
        point.put("temp", hour.getDouble("temp_c").toFloat().toDouble())
        point.put("feels_like", hour.getDouble("feelslike_c").toFloat().toDouble())
        point.put("pop", (hour.optInt("chance_of_rain", 0) / 100.0).toFloat().toDouble())
        point.put(
            "weather",
            conditionArray(hour.getJSONObject("condition"), hour.optInt("is_day", 1) == 1),
        )
        return point
    }

    private fun conditionArray(condition: JSONObject, day: Boolean): JSONArray =
        JSONArray().put(translateCondition(condition, day))

    private fun translateDay(dayNode: JSONObject, zoneId: String): JSONObject {
        val day = dayNode.getJSONObject("day")
        val astro = dayNode.getJSONObject("astro")
        val date = dayNode.getString("date")
        val average = day.getDouble("avgtemp_c")

        val point = JSONObject()
        point.put("dt", dayNode.getLong("date_epoch"))
        point.put("sunrise", parseAstro(date, astro.optString("sunrise", ""), zoneId))
        point.put("sunset", parseAstro(date, astro.optString("sunset", ""), zoneId))
        point.put("temp", translateTemperatures(day, dayNode, zoneId, average))
        point.put("pop", (day.optInt("daily_chance_of_rain", 0) / 100.0).toFloat().toDouble())
        point.put("weather", conditionArray(day.getJSONObject("condition"), true))

        return point
    }

    private fun translateTemperatures(
        day: JSONObject,
        dayNode: JSONObject,
        zoneId: String,
        average: Double,
    ): JSONObject {
        val temperatures = JSONObject()
        temperatures.put("day", day.getDouble("avgtemp_c").toFloat().toDouble())
        temperatures.put("min", day.getDouble("mintemp_c").toFloat().toDouble())
        temperatures.put("max", day.getDouble("maxtemp_c").toFloat().toDouble())
        temperatures.put("night", meanForHours(dayNode, zoneId, 0, 5, average).toFloat().toDouble())
        temperatures.put("eve", meanForHours(dayNode, zoneId, 18, 23, average).toFloat().toDouble())
        temperatures.put("morn", meanForHours(dayNode, zoneId, 6, 11, average).toFloat().toDouble())
        return temperatures
    }

    private fun meanForHours(
        dayNode: JSONObject,
        zoneId: String,
        from: Int,
        to: Int,
        fallback: Double,
    ): Double {
        try {
            val zone = TimeZone.getTimeZone(zoneId)
            val hours = dayNode.getJSONArray("hour")

            var sum = 0.0
            var count = 0

            for (index in 0 until hours.length()) {
                val hour = hours.getJSONObject(index)
                val localHour = localHour(hour.getLong("time_epoch"), zone)
                if (localHour !in from..to) continue
                sum += hour.getDouble("temp_c")
                count++
            }

            if (count == 0) return fallback
            return sum / count
        } catch (exception: Exception) {
            return fallback
        }
    }

    private fun parseAstro(date: String, time: String, zoneId: String): Long {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
            format.timeZone = TimeZone.getTimeZone(zoneId)
            return checkNotNull(format.parse("$date $time")) {
                "Weather timestamp could not be parsed"
            }.time / 1000
        } catch (exception: Exception) {
            return 0
        }
    }

    private fun translateCondition(condition: JSONObject, day: Boolean): JSONObject {
        val mapped = mapCondition(condition.getInt("code"))

        val result = JSONObject()
        result.put("id", mapped.openWeatherId)
        result.put("description", condition.optString("text", ""))
        result.put(
            "icon",
            String.format(Locale.US, "%02d", mapped.iconPrefix) + if (day) "d" else "n",
        )

        return result
    }

    private fun mapCondition(code: Int): ConditionMapping {
        for (row in CONDITION_MAP) if (row.weatherApiCode == code) return row
        return FALLBACK_CONDITION
    }

    private fun apiError(error: JSONObject?, cause: Exception?): RuntimeException {
        if (error == null) return RuntimeException("Weather service returned an error", cause)
        val message = error.optString("message", "Weather service returned an error")

        return when (error.optInt("code", -1)) {
            1002, 2006 -> RuntimeException("WeatherAPI key invalid ($message)", cause)

            2007 -> RuntimeException("WeatherAPI quota exceeded ($message)", cause)
            1006 -> RuntimeException(
                "Location unavailable - allow location access or set a fixed location",
                cause,
            )

            else -> RuntimeException(message, cause)
        }
    }

    private class LangMapping(val lang: String, val result: String = lang)

    private class ConditionMapping(
        val weatherApiCode: Int,
        val openWeatherId: Int,
        val iconPrefix: Int,
    )

    private inner class FetchWorker : Thread(WORKER_NAME) {
        @Volatile
        private var done = false

        @Volatile
        var json: String? = null

        @Volatile
        var failure: Throwable? = null

        init {
            isDaemon = true
        }

        fun finished(): Boolean = done

        override fun run() {
            try {
                if (currentApiKey().isEmpty()) throw RuntimeException(
                    "No WeatherAPI key - enter one below the temperature-units button"
                )
                val coordinates = resolveCoords()
                val language = mapLang(readLang())

                val root = JSONObject(download(coordinates[0], coordinates[1], language))
                if (root.has("error")) throw apiError(root.getJSONObject("error"), null)

                val response = translate(root, coordinates[0], coordinates[1], language).toString()
                json = response
                storeCache(response)
            } catch (exception: Throwable) {
                failure = exception
            } finally {
                done = true
                synchronized(fetchLock) {
                    if (inFlight === this) inFlight = null
                }
            }
        }
    }

    companion object {
        private const val PREFS = "bitpit.launcher_preferences"
        private const val KEY_DYNAMIC = "bitpit.launcher.key.WEATHER_LOCATION_IS_DYNAMIC"
        private const val KEY_LAT = "bitpit.launcher.key.WEATHER_LOCATION_LAT"
        private const val KEY_LONG = "bitpit.launcher.key.WEATHER_LOCATION_LONG"
        private const val KEY_LANG = "bitpit.launcher.key.WEATHER_LANGUAGE"
        private const val KEY_API_RUNTIME = "madkarma.weather.apiKey"
        private const val ENDPOINT = "https://api.weatherapi.com/v1/forecast.json"
        private const val FORECAST_DAYS = 3
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"
        private const val WORKER_NAME = "madkarma-weather-fetch"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
        private const val FETCH_GRACE_MS = 2500
        private const val KEY_CACHE_JSON = "madkarma.weather.lastJson"
        private const val KEY_CACHE_TIME = "madkarma.weather.lastTime"
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
        private val fetchLock = Any()
        private val FALLBACK_CONDITION = ConditionMapping(-1, 804, 4)
        private val CONDITION_MAP = arrayOf(
            ConditionMapping(1000, 800, 1),
            ConditionMapping(1003, 801, 2),
            ConditionMapping(1006, 802, 3),
            ConditionMapping(1009, 804, 4),
            ConditionMapping(1030, 701, 50),
            ConditionMapping(1135, 741, 50),
            ConditionMapping(1147, 741, 50),
            ConditionMapping(1063, 500, 10),
            ConditionMapping(1150, 500, 10),
            ConditionMapping(1153, 500, 10),
            ConditionMapping(1180, 500, 10),
            ConditionMapping(1183, 500, 10),
            ConditionMapping(1198, 500, 10),
            ConditionMapping(1240, 500, 10),
            ConditionMapping(1186, 501, 10),
            ConditionMapping(1189, 501, 10),
            ConditionMapping(1192, 501, 10),
            ConditionMapping(1195, 501, 10),
            ConditionMapping(1201, 501, 10),
            ConditionMapping(1243, 501, 10),
            ConditionMapping(1246, 501, 10),
            ConditionMapping(1087, 200, 11),
            ConditionMapping(1273, 200, 11),
            ConditionMapping(1276, 200, 11),
            ConditionMapping(1279, 200, 11),
            ConditionMapping(1282, 200, 11),
            ConditionMapping(1066, 600, 13),
            ConditionMapping(1069, 600, 13),
            ConditionMapping(1072, 600, 13),
            ConditionMapping(1114, 600, 13),
            ConditionMapping(1117, 600, 13),
            ConditionMapping(1204, 600, 13),
            ConditionMapping(1207, 600, 13),
            ConditionMapping(1210, 600, 13),
            ConditionMapping(1213, 600, 13),
            ConditionMapping(1216, 600, 13),
            ConditionMapping(1219, 600, 13),
            ConditionMapping(1222, 600, 13),
            ConditionMapping(1225, 600, 13),
            ConditionMapping(1237, 600, 13),
            ConditionMapping(1249, 600, 13),
            ConditionMapping(1252, 600, 13),
            ConditionMapping(1255, 600, 13),
            ConditionMapping(1258, 600, 13),
            ConditionMapping(1261, 600, 13),
            ConditionMapping(1264, 600, 13),
        )
        private val LANG_MAP = arrayOf(
            LangMapping("ar"),
            LangMapping("bn"),
            LangMapping("bg"),
            LangMapping("da"),
            LangMapping("nl"),
            LangMapping("fi"),
            LangMapping("fr"),
            LangMapping("de"),
            LangMapping("el"),
            LangMapping("hi"),
            LangMapping("hu"),
            LangMapping("it"),
            LangMapping("ja"),
            LangMapping("jv"),
            LangMapping("ko"),
            LangMapping("mr"),
            LangMapping("pl"),
            LangMapping("pt"),
            LangMapping("pa"),
            LangMapping("ro"),
            LangMapping("ru"),
            LangMapping("sr"),
            LangMapping("si"),
            LangMapping("sk"),
            LangMapping("es"),
            LangMapping("sv"),
            LangMapping("ta"),
            LangMapping("te"),
            LangMapping("tr"),
            LangMapping("uk"),
            LangMapping("ur"),
            LangMapping("vi"),
            LangMapping("zh"),
            LangMapping("zh_tw"),
            LangMapping("cz", "cs"),
            LangMapping("zh_cmn", "zh"),
        )
        private var inFlight: FetchWorker? = null

        private fun localHour(epochSeconds: Long, zone: TimeZone): Int {
            var hour =
                ((epochSeconds + zone.getOffset(epochSeconds * 1000L) / 1000L) / 3600 % 24).toInt()
            if (hour < 0) hour += 24
            return hour
        }

        @Export
        @JvmStatic
        fun create(context: Context): WeatherApi = WeatherApi(context)
    }
}
