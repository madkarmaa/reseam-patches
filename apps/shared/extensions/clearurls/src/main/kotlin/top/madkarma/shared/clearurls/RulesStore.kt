// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.shared.clearurls

import android.content.Context
import android.util.Log
import org.json.JSONException
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

internal const val TAG = "ClearURLs"

/** Loads the cached rules, then refreshes them from ClearURLs when the published hash changes. */
internal object RulesStore {
    private const val DATA_URL = "https://rules2.clearurls.xyz/data.minify.json"
    private const val HASH_URL = "https://rules2.clearurls.xyz/rules.minify.hash"
    private const val CACHE_NAME = "clearurls-rules.json"
    private const val TIMEOUT_MS = 15_000

    // A link cleaned before the first load finishes waits at most this long.
    private const val FIRST_LOAD_WAIT_MS = 3_000L

    private val started = AtomicBoolean()
    private val firstLoad = CountDownLatch(1)

    @Volatile
    private var rules: Rules? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return

        val cache = File(context.applicationContext.noBackupFilesDir, CACHE_NAME)
        thread(name = TAG, isDaemon = true) {
            try {
                refresh(cache)
            } finally {
                firstLoad.countDown()
            }
        }
    }

    fun current(): Rules? {
        rules?.let { return it }
        if (!started.get()) return null

        try {
            firstLoad.await(FIRST_LOAD_WAIT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        return rules
    }

    private fun refresh(cache: File) {
        try {
            val cached = cache.takeIf { it.isFile }?.readBytes()
            cached?.let { loadRules(it, "cache") }
            if (rules != null) firstLoad.countDown()

            val hash = download(HASH_URL).toString(Charsets.UTF_8).trim().lowercase()
            if (cached != null && rules != null && sha256(cached) == hash) return

            val data = download(DATA_URL)
            if (sha256(data) != hash) {
                Log.w(TAG, "Downloaded rules do not match their hash")
                return
            }
            if (!loadRules(data, "network")) return

            // Each process of the app refreshes on its own, so each writes its own partial file.
            val partial = File.createTempFile(CACHE_NAME, ".tmp", cache.parentFile)
            try {
                partial.writeBytes(data)
                if (!partial.renameTo(cache)) Log.w(TAG, "Cannot cache the rules in $cache")
            } finally {
                partial.delete()
            }
        } catch (exception: IOException) {
            Log.w(TAG, "Cannot refresh the rules: $exception")
        } catch (exception: RuntimeException) {
            // An uncaught exception on this thread would crash the app.
            Log.w(TAG, "Cannot refresh the rules", exception)
        }
    }

    private fun loadRules(data: ByteArray, source: String): Boolean {
        val parsed = try {
            Rules.parse(data.toString(Charsets.UTF_8))
        } catch (exception: JSONException) {
            Log.w(TAG, "Ignoring unreadable rules from $source: $exception")
            return false
        }

        rules = parsed
        Log.i(TAG, "Loaded ${parsed.size} providers from $source")
        return true
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpsURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS

        try {
            val status = connection.responseCode
            if (status != HttpsURLConnection.HTTP_OK) throw IOException("HTTP $status for $url")

            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(data: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }
}
