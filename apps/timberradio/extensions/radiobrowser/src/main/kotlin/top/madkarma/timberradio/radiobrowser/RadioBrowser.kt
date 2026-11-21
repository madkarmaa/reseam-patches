// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("RadioBrowser")
@file:Suppress("unused")

package top.madkarma.timberradio.radiobrowser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.reseam.runtime.Export
import com.msoft.timberradio.db.room.Stream
import com.msoft.timberradio.model.Station
import com.msoft.timberradio.network.NetworkResult
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "RadioBrowser"

// Timber pages hold 50 stations; matching that keeps both sources paging together.
private const val PAGE_SIZE = 50
private const val TIMEOUT_MILLIS = 8000
private const val USER_AGENT = "TimberRadio-RadioBrowser/1.0"

// all.api resolves to every mirror; the named mirrors are fallbacks when that lookup fails.
private val servers =
    listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "de2.api.radio-browser.info")

// Timber data type name to the radio-browser search parameter that takes the same query.
private val searchParameters = mapOf("SEARCH" to "name", "STATIONS_BY_COUNTRY" to "countrycode")

private val executor = Executors.newCachedThreadPool()
private val mainThread = Handler(Looper.getMainLooper())

/**
 * Wraps a Timber station-list callback so successful results also carry matching radio-browser.info
 * stations. Data types without a radio-browser equivalent keep the original callback.
 */
@Export
fun withRadioBrowserStations(
    dataType: Any?,
    query: String?,
    page: Int,
    callback: ((Any?) -> Any?)?,
): ((Any?) -> Any?)? {
    val parameter = searchParameters[(dataType as? Enum<*>)?.name] ?: return callback
    if (callback == null || query.isNullOrBlank()) return callback

    return MergingCallback(parameter, query, page, callback)
}

private class MergingCallback(
    private val parameter: String,
    private val query: String,
    private val page: Int,
    private val callback: (Any?) -> Any?,
) : (Any?) -> Any? {
    override fun invoke(result: Any?): Any? {
        if (result !is NetworkResult.Success) return callback(result)
        val timberStations = result.data as? List<*> ?: return callback(result)

        executor.execute {
            val merged = ArrayList<Any?>(timberStations)
            merged.addAll(newStations(timberStations, fetchStations()))

            Log.i(TAG, "$parameter=$query page $page: ${merged.size - timberStations.size} added")
            mainThread.post { callback(NetworkResult.Success(merged)) }
        }
        return null
    }

    private fun fetchStations(): JSONArray {
        val path =
            Uri.Builder()
                .path("/json/stations/search")
                .appendQueryParameter(parameter, query)
                .appendQueryParameter("hidebroken", "true")
                .appendQueryParameter("order", "clickcount")
                .appendQueryParameter("reverse", "true")
                .appendQueryParameter("limit", PAGE_SIZE.toString())
                .appendQueryParameter("offset", (page * PAGE_SIZE).toString())
                .build()
                .toString()

        for (server in servers) {
            try {
                return JSONArray(download("https://$server$path"))
            } catch (e: Exception) {
                Log.w(TAG, "$server failed: $e")
            }
        }
        return JSONArray()
    }
}

private fun download(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** Converts radio-browser entries to Timber stations, skipping ones Timber already lists. */
private fun newStations(timberStations: List<*>, entries: JSONArray): List<Station> {
    val seenNames = HashSet<String>()
    val seenStreams = HashSet<String>()
    for (station in timberStations.filterIsInstance<Station>()) {
        station.name?.let { seenNames.add(it.trim().lowercase()) }
        station.listen?.filterIsInstance<Stream>()?.forEach { stream ->
            stream.listen_url?.let { seenStreams.add(streamKey(it)) }
        }
    }

    val stations = ArrayList<Station>()
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        val streamUrl = entry.optString("url_resolved").ifBlank { entry.optString("url") }
        val name = entry.optString("name").trim()

        if (streamUrl.isBlank() || name.isEmpty()) continue
        if (!seenNames.add(name.lowercase())) continue
        if (!seenStreams.add(streamKey(streamUrl))) continue

        stations.add(toStation(entry, name, streamUrl))
    }
    return stations
}

private fun streamKey(url: String) = url.trim().lowercase().substringAfter("://").trimEnd('/', ';')

private fun toStation(entry: JSONObject, name: String, streamUrl: String): Station {
    val uuid = entry.optString("stationuuid")
    val mediaType = if (entry.optInt("hls") == 1) "hls" else entry.optString("codec").lowercase()
    val bitrate = entry.optInt("bitrate")
    val stream =
        Stream(0, streamUrl, if (bitrate > 0) bitrate.toString() else "", mediaType, "1", true)

    return Station(
        uuid.hashCode(),
        "rb-$uuid", // Favorites and recents are keyed by station code.
        name,
        "",
        "",
        "Stream",
        entry.optString("homepage"),
        "",
        entry.optString("favicon"),
        entry.optString("country"),
        entry.optString("countrycode"),
        "",
        "",
        "",
        "",
        entry.optString("language"),
        "",
        "",
        entry.optString("tags").substringBefore(',').trim(),
        "",
        entry.optString("state"),
        "",
        "",
        "1",
        "",
        arrayListOf(stream),
        0L,
    )
}
