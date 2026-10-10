package top.madkarma.extensions;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

/**
 * Serves Niagara's weather widget from WeatherAPI.com instead of the
 * Niagarald backend, which needs a server-issued session token.
 *
 * <p>Returns JSON in Niagara's {@code WeatherResponse} schema, with timestamp,
 * provider, lat, long, lang, and forecast fields. The app reads it through
 * its existing parser, cache, and UI models.
 */
@SuppressWarnings("unused")
public final class WeatherApi {
    private static final String PREFS = "bitpit.launcher_preferences";
    private static final String KEY_DYNAMIC = "bitpit.launcher.key.WEATHER_LOCATION_IS_DYNAMIC";
    private static final String KEY_LAT = "bitpit.launcher.key.WEATHER_LOCATION_LAT";
    private static final String KEY_LONG = "bitpit.launcher.key.WEATHER_LOCATION_LONG";
    private static final String KEY_LANG = "bitpit.launcher.key.WEATHER_LANGUAGE";
    private static final String KEY_API_RUNTIME = "madkarma.weather.apiKey";

    private static final String ENDPOINT = "https://api.weatherapi.com/v1/forecast.json";
    private static final int FORECAST_DAYS = 3;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36";

    private static final String WORKER_NAME = "madkarma-weather-fetch";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int FETCH_GRACE_MS = 2500;

    private static final String KEY_CACHE_JSON = "madkarma.weather.lastJson";
    private static final String KEY_CACHE_TIME = "madkarma.weather.lastTime";
    private static final long CACHE_TTL_MS = 10L * 60L * 1000L;

    private static final Object fetchLock = new Object();

    /**
     * Unknown codes fall back to overcast, which always renders.
     */
    private static final ConditionMapping FALLBACK_CONDITION = new ConditionMapping(-1, 804, 4);

    private static final ConditionMapping[] CONDITION_MAP = {
        // Clear and partly cloudy.
        new ConditionMapping(1000, 800, 1), new ConditionMapping(1003, 801, 2),
        // Cloudy and overcast.
        new ConditionMapping(1006, 802, 3), new ConditionMapping(1009, 804, 4),
        // Mist and fog.
        new ConditionMapping(1030, 701, 50), new ConditionMapping(1135, 741, 50), new ConditionMapping(1147, 741, 50),
        // Light rain and drizzle.
        new ConditionMapping(1063, 500, 10), new ConditionMapping(1150, 500, 10), new ConditionMapping(1153, 500, 10), new ConditionMapping(1180, 500, 10), new ConditionMapping(1183, 500, 10), new ConditionMapping(1198, 500, 10), new ConditionMapping(1240, 500, 10),
        // Moderate and heavy rain.
        new ConditionMapping(1186, 501, 10), new ConditionMapping(1189, 501, 10), new ConditionMapping(1192, 501, 10), new ConditionMapping(1195, 501, 10), new ConditionMapping(1201, 501, 10), new ConditionMapping(1243, 501, 10), new ConditionMapping(1246, 501, 10),
        // Thunderstorms.
        new ConditionMapping(1087, 200, 11), new ConditionMapping(1273, 200, 11), new ConditionMapping(1276, 200, 11), new ConditionMapping(1279, 200, 11), new ConditionMapping(1282, 200, 11),
        // Snow, sleet and ice.
        new ConditionMapping(1066, 600, 13), new ConditionMapping(1069, 600, 13), new ConditionMapping(1072, 600, 13), new ConditionMapping(1114, 600, 13), new ConditionMapping(1117, 600, 13), new ConditionMapping(1204, 600, 13), new ConditionMapping(1207, 600, 13), new ConditionMapping(1210, 600, 13), new ConditionMapping(1213, 600, 13), new ConditionMapping(1216, 600, 13), new ConditionMapping(1219, 600, 13), new ConditionMapping(1222, 600, 13), new ConditionMapping(1225, 600, 13), new ConditionMapping(1237, 600, 13), new ConditionMapping(1249, 600, 13), new ConditionMapping(1252, 600, 13), new ConditionMapping(1255, 600, 13), new ConditionMapping(1258, 600, 13), new ConditionMapping(1261, 600, 13), new ConditionMapping(1264, 600, 13),};

    private static final LangMapping[] LANG_MAP = {new LangMapping("ar"), new LangMapping("bn"), new LangMapping("bg"), new LangMapping("da"), new LangMapping("nl"), new LangMapping("fi"), new LangMapping("fr"), new LangMapping("de"), new LangMapping("el"), new LangMapping("hi"), new LangMapping("hu"), new LangMapping("it"), new LangMapping("ja"), new LangMapping("jv"), new LangMapping("ko"), new LangMapping("mr"), new LangMapping("pl"), new LangMapping("pt"), new LangMapping("pa"), new LangMapping("ro"), new LangMapping("ru"), new LangMapping("sr"), new LangMapping("si"), new LangMapping("sk"), new LangMapping("es"), new LangMapping("sv"), new LangMapping("ta"), new LangMapping("te"), new LangMapping("tr"), new LangMapping("uk"), new LangMapping("ur"), new LangMapping("vi"), new LangMapping("zh"), new LangMapping("zh_tw"), new LangMapping("cz", "cs"), new LangMapping("zh_cmn", "zh"),};

    // The worker holds only the application context. It clears this reference
    // when it finishes.
    @SuppressWarnings("StaticFieldLeak")
    private static FetchWorker inFlight;

    private final Context appContext;

    private WeatherApi(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Creates a client with the application context. Fetches read the key from settings.
     */
    public static WeatherApi create(Context context) {
        return new WeatherApi(context);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Returns the local hour for an epoch timestamp in seconds.
     */
    private static int localHour(long epochSeconds, TimeZone zone) {
        int hour = (int) (((epochSeconds + zone.getOffset(epochSeconds * 1000L) / 1000L) / 3600) % 24);
        if (hour < 0) {
            hour += 24;
        }
        return hour;
    }

    /**
     * Fails fetch setup with a message telling the user where the key goes.
     */
    private static RuntimeException missingKey() {
        return new RuntimeException("No WeatherAPI key - enter one below the temperature-units button");
    }

    public long now() {
        return System.currentTimeMillis();
    }

    /**
     * Fetches current weather and a three-day forecast as Niagara backend JSON.
     * A worker handles the network request. The caller waits up to 2.5 seconds,
     * then throws a loading failure if the worker has not finished. The worker
     * continues and caches a successful response for the next call.
     */
    public String fetch() {
        String cached = freshCache();
        if (cached != null) {
            return cached;
        }

        FetchWorker worker = startWorker();

        try {
            worker.join(FETCH_GRACE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (worker.finished()) {
            if (worker.failure != null) {
                throw sneakyThrow(asNoWeather(worker.failure));
            }

            return worker.json;
        }

        throw sneakyThrow(buildNoWeather("Refreshing weather...", null));
    }

    private FetchWorker startWorker() {
        synchronized (fetchLock) {
            if (inFlight == null || !inFlight.isAlive()) {
                inFlight = new FetchWorker();
                inFlight.start();
            }

            return inFlight;
        }
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Returns the last successful response if its cache entry has not expired.
     */
    private String freshCache() {
        SharedPreferences prefs = prefs();

        long at = prefs.getLong(KEY_CACHE_TIME, 0);
        if (at <= 0 || now() - at > CACHE_TTL_MS) {
            return null;
        }

        String json = prefs.getString(KEY_CACHE_JSON, null);
        if (json == null || json.isEmpty()) {
            return null;
        }

        return json;
    }

    private void storeCache(String json) {
        prefs().edit().putString(KEY_CACHE_JSON, json).putLong(KEY_CACHE_TIME, now()).apply();
    }

    /**
     * Returns the app's {@code NoWeatherDataException} for the widget's error card.
     * Callers throw the result with {@code throw sneakyThrow(...)} outside the
     * try block. Throwing inside it would let the catch intercept the exception
     * and terminate the thread.
     */
    private Throwable asNoWeather(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isEmpty()) {
            message = "Failed to read weather data";
        }
        return buildNoWeather(message, failure instanceof Exception ? (Exception) failure : null);
    }

    private Throwable buildNoWeather(String message, Exception cause) {
        try {
            Class<?> type = Class.forName("bitpit.launcher.weather.NoWeatherDataException");
            java.lang.reflect.Constructor<?> ctor = type.getConstructor(Integer.TYPE, Exception.class, String.class);
            return (Throwable) ctor.newInstance(6, cause, message);
        } catch (Exception e) {
            return new RuntimeException(message, cause != null ? cause : e);
        }
    }

    private double[] resolveCoords() {
        SharedPreferences prefs = prefs();

        if (!prefs.getBoolean(KEY_DYNAMIC, false) && prefs.contains(KEY_LAT) && prefs.contains(KEY_LONG)) {
            return new double[]{prefs.getFloat(KEY_LAT, 0f), prefs.getFloat(KEY_LONG, 0f)};
        }

        Location location = lastKnown();
        if (location == null) {
            throw new RuntimeException("Location unavailable - allow location access or set a fixed location");
        }

        return new double[]{location.getLatitude(), location.getLongitude()};
    }

    // Check permission before reading locations, and catch SecurityException
    // in case Android revokes permission during the read.
    @SuppressWarnings("MissingPermission")
    private Location lastKnown() {
        if (Build.VERSION.SDK_INT >= 23 && appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        LocationManager manager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }

        Location best = null;
        String[] providers = {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,};
        for (String provider : providers) {
            Location candidate;
            try {
                candidate = manager.getLastKnownLocation(provider);
            } catch (IllegalArgumentException | SecurityException e) {
                continue;
            }

            if (candidate == null) {
                continue;
            }

            if (best == null || candidate.getTime() > best.getTime()) {
                best = candidate;
            }
        }

        return best;
    }

    private String readLang() {
        String stored = prefs().getString(KEY_LANG, null);
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        return Locale.getDefault().getLanguage();
    }

    /**
     * Maps Niagara or device language tags to WeatherAPI codes. Defaults to English.
     */
    private String mapLang(String lang) {
        if (lang == null) {
            return "en";
        }

        for (LangMapping row : LANG_MAP) {
            if (row.lang.equals(lang)) {
                return row.into();
            }
        }

        return "en";
    }

    private String download(double lat, double lon, String lang) {
        HttpsURLConnection connection = null;
        try {
            String url = requestUrl(lat, lon, lang);
            connection = (HttpsURLConnection) new URL(url).openConnection();

            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);

            int status = connection.getResponseCode();
            InputStream stream = status == 200 ? connection.getInputStream() : connection.getErrorStream();
            String body = readAll(stream);

            if (status != 200) {
                throw apiError(parseError(body), null);
            }

            return body;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reach weather service", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String requestUrl(double lat, double lon, String lang) {
        return ENDPOINT + "?key=" + currentApiKey() + "&q=" + lat + "," + lon + "&days=" + FORECAST_DAYS + "&aqi=no&alerts=no&lang=" + lang;
    }

    /**
     * Returns the saved API key, or an empty string if none has been entered.
     */
    public String currentApiKey() {
        String saved = prefs().getString(KEY_API_RUNTIME, null);
        if (saved != null && !saved.isEmpty()) {
            return saved;
        }
        return "";
    }

    /**
     * Saves the API key and clears the cache so the next fetch uses it.
     */
    public void saveApiKey(String key) {
        String value = key == null ? "" : key.trim();
        prefs().edit().putString(KEY_API_RUNTIME, value).remove(KEY_CACHE_JSON).remove(KEY_CACHE_TIME).apply();
    }

    private JSONObject parseError(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.has("error")) {
                return root.getJSONObject("error");
            }
        } catch (Exception e) {
            // Fall through to the generic message below.
        }
        return null;
    }

    private String readAll(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buffer = new byte[8192];
        int read;

        while ((read = stream.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    private JSONObject translate(JSONObject root, double lat, double lon, String lang) throws Exception {
        JSONObject location = root.getJSONObject("location");
        JSONArray days = root.getJSONObject("forecast").getJSONArray("forecastday");
        String zoneId = location.optString("tz_id", "UTC");

        JSONObject forecast = new JSONObject();
        forecast.put("current", translateCurrent(root.getJSONObject("current")));
        forecast.put("minutely", new JSONArray());
        forecast.put("hourly", translateHourly(days));
        forecast.put("daily", translateDaily(days, zoneId));

        JSONObject envelope = new JSONObject();
        envelope.put("timestamp", System.currentTimeMillis());
        envelope.put("provider", "WeatherAPI.com");
        envelope.put("lat", String.valueOf(lat));
        envelope.put("long", String.valueOf(lon));
        envelope.put("lang", lang);
        envelope.put("forecast", forecast);
        return envelope;
    }

    private JSONArray translateHourly(JSONArray days) throws Exception {
        JSONArray hourly = new JSONArray();
        for (int d = 0; d < days.length(); d++) {
            JSONArray hours = days.getJSONObject(d).getJSONArray("hour");
            for (int h = 0; h < hours.length(); h++) {
                hourly.put(translateHour(hours.getJSONObject(h)));
            }
        }
        return hourly;
    }

    private JSONArray translateDaily(JSONArray days, String zoneId) throws Exception {
        JSONArray daily = new JSONArray();
        for (int d = 0; d < days.length(); d++) {
            daily.put(translateDay(days.getJSONObject(d), zoneId));
        }
        return daily;
    }

    private JSONObject translateCurrent(JSONObject current) throws Exception {
        JSONObject point = new JSONObject();
        point.put("dt", current.getLong("last_updated_epoch"));
        point.put("temp", (float) current.getDouble("temp_c"));
        point.put("feels_like", (float) current.getDouble("feelslike_c"));
        point.put("weather", conditionArray(current.getJSONObject("condition"), current.optInt("is_day", 1) == 1));
        return point;
    }

    private JSONObject translateHour(JSONObject hour) throws Exception {
        JSONObject point = new JSONObject();
        point.put("dt", hour.getLong("time_epoch"));
        point.put("temp", (float) hour.getDouble("temp_c"));
        point.put("feels_like", (float) hour.getDouble("feelslike_c"));
        point.put("pop", (float) (hour.optInt("chance_of_rain", 0) / 100.0));
        point.put("weather", conditionArray(hour.getJSONObject("condition"), hour.optInt("is_day", 1) == 1));
        return point;
    }

    private JSONArray conditionArray(JSONObject condition, boolean day) throws Exception {
        return new JSONArray().put(translateCondition(condition, day));
    }

    private JSONObject translateDay(JSONObject dayNode, String zoneId) throws Exception {
        JSONObject day = dayNode.getJSONObject("day");
        JSONObject astro = dayNode.getJSONObject("astro");
        String date = dayNode.getString("date");
        double average = day.getDouble("avgtemp_c");

        JSONObject point = new JSONObject();
        point.put("dt", dayNode.getLong("date_epoch"));
        point.put("sunrise", parseAstro(date, astro.optString("sunrise", ""), zoneId));
        point.put("sunset", parseAstro(date, astro.optString("sunset", ""), zoneId));
        point.put("temp", translateTemperatures(day, dayNode, zoneId, average));
        point.put("pop", (float) (day.optInt("daily_chance_of_rain", 0) / 100.0));
        point.put("weather", conditionArray(day.getJSONObject("condition"), true));
        return point;
    }

    private JSONObject translateTemperatures(JSONObject day, JSONObject dayNode, String zoneId, double average) throws Exception {
        JSONObject temps = new JSONObject();
        temps.put("day", (float) day.getDouble("avgtemp_c"));
        temps.put("min", (float) day.getDouble("mintemp_c"));
        temps.put("max", (float) day.getDouble("maxtemp_c"));
        temps.put("night", (float) meanForHours(dayNode, zoneId, 0, 5, average));
        temps.put("eve", (float) meanForHours(dayNode, zoneId, 18, 23, average));
        temps.put("morn", (float) meanForHours(dayNode, zoneId, 6, 11, average));
        return temps;
    }

    /**
     * Averages the day's hourly temperatures within the requested hour range.
     */
    private double meanForHours(JSONObject dayNode, String zoneId, int from, int to, double fallback) {
        try {
            TimeZone zone = TimeZone.getTimeZone(zoneId);
            JSONArray hours = dayNode.getJSONArray("hour");

            double sum = 0;
            int count = 0;

            for (int h = 0; h < hours.length(); h++) {
                JSONObject hour = hours.getJSONObject(h);
                if (localHour(hour.getLong("time_epoch"), zone) < from || localHour(hour.getLong("time_epoch"), zone) > to) {
                    continue;
                }

                sum += hour.getDouble("temp_c");
                count++;
            }

            if (count == 0) {
                return fallback;
            }

            return sum / count;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parses the date and "hh:mm a" time in the location's time zone as epoch seconds.
     * Returns zero on failure so the caller can omit the value.
     */
    private long parseAstro(String date, String time, String zoneId) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone(zoneId));
            return format.parse(date + " " + time).getTime() / 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private JSONObject translateCondition(JSONObject condition, boolean day) throws Exception {
        int code = condition.getInt("code");
        ConditionMapping mapped = mapCondition(code);

        JSONObject out = new JSONObject();
        out.put("id", mapped.openWeatherId);
        out.put("description", condition.optString("text", ""));
        out.put("icon", String.format(Locale.US, "%02d", mapped.iconPrefix) + (day ? "d" : "n"));
        return out;
    }

    /**
     * Looks up the WeatherAPI condition code in the map above.
     */
    private ConditionMapping mapCondition(int code) {
        for (ConditionMapping row : CONDITION_MAP) {
            if (row.weatherApiCode == code) {
                return row;
            }
        }
        return FALLBACK_CONDITION;
    }

    private RuntimeException apiError(JSONObject error, Exception cause) {
        if (error == null) {
            return new RuntimeException("Weather service returned an error", cause);
        }

        int code = error.optInt("code", -1);
        String message = error.optString("message", "Weather service returned an error");

        return switch (code) {
            case 1002, 2006 ->
                new RuntimeException("WeatherAPI key invalid (" + message + ")", cause);
            case 2007 -> new RuntimeException("WeatherAPI quota exceeded (" + message + ")", cause);
            case 1006 ->
                new RuntimeException("Location unavailable - allow location access or set a fixed location", cause);
            default -> new RuntimeException(message, cause);
        };
    }

    /**
     * Maps a Niagara or device language tag to a WeatherAPI code.
     * The one-argument constructor uses the tag as the code. Pass both
     * arguments when WeatherAPI uses a different code.
     */
    @SuppressWarnings("ClassCanBeRecord") // records aren't supported in older Android versions
    private static final class LangMapping {
        final String lang;
        final String result;

        LangMapping(String lang) {
            this(lang, lang);
        }

        LangMapping(String lang, String result) {
            this.lang = lang;
            this.result = result;
        }

        String into() {
            return result;
        }
    }

    /**
     * Maps a WeatherAPI code to the OpenWeather ID and icon prefix Niagara expects.
     * Niagara selects icons by ID range and a {@code d} or {@code n} suffix.
     */
    @SuppressWarnings("ClassCanBeRecord") // records aren't supported in older Android versions
    private static final class ConditionMapping {
        final int weatherApiCode;
        final int openWeatherId;
        final int iconPrefix;

        ConditionMapping(int weatherApiCode, int openWeatherId, int iconPrefix) {
            this.weatherApiCode = weatherApiCode;
            this.openWeatherId = openWeatherId;
            this.iconPrefix = iconPrefix;
        }
    }

    private final class FetchWorker extends Thread {
        private volatile boolean done;
        private volatile String json;
        private volatile Throwable failure;

        FetchWorker() {
            setName(WORKER_NAME);
            setDaemon(true);
        }

        boolean finished() {
            return done;
        }

        @Override
        public void run() {
            try {
                if (currentApiKey().isEmpty()) {
                    throw missingKey();
                }

                double[] coords = resolveCoords();
                String lang = mapLang(readLang());

                String body = download(coords[0], coords[1], lang);
                JSONObject root = new JSONObject(body);
                if (root.has("error")) {
                    throw apiError(root.getJSONObject("error"), null);
                }

                json = translate(root, coords[0], coords[1], lang).toString();

                storeCache(json);
            } catch (Throwable t) {
                failure = t;
            } finally {
                done = true;
                synchronized (fetchLock) {
                    if (inFlight == this) {
                        inFlight = null;
                    }
                }
            }
        }
    }
}
