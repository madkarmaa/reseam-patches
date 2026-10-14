// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.droplert.extensions

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.PreferencesProto
import app.reseam.runtime.Export
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Overrides DataStore preferences before the application opens its stores. */
object Prefs {
    private const val TAG = "Prefs"
    private val stores = arrayOf("user_preferences")

    @Export
    @JvmStatic
    fun putBoolean(context: Context, key: String, value: Boolean) {
        val directory = File(context.filesDir, "datastore")
        if (!directory.isDirectory && !directory.mkdirs()) {
            Log.w(TAG, "datastore dir unavailable")
            return
        }
        val entry = PreferencesProto.Value.newBuilder().setBoolean(value).build()
        for (store in stores) mergeKey(File(directory, "$store.preferences_pb"), key, entry)
    }

    private fun mergeKey(file: File, key: String, value: PreferencesProto.Value) {
        try {
            val preferences = if (file.isFile) {
                val length = file.length()
                if (length > 1 shl 20) throw IOException("preferences file implausibly large: $length")

                val data = ByteArray(length.toInt())
                DataInputStream(file.inputStream()).use { it.readFully(data) }

                PreferencesProto.PreferenceMap.parseFrom(data).toBuilder()
            } else PreferencesProto.PreferenceMap.newBuilder()

            preferences.putPreferences(key, value)

            val data = preferences.build().toByteArray()
            FileOutputStream(file).use { stream ->
                stream.write(data)
                stream.fd.sync()
            }
        } catch (exception: IOException) {
            Log.w(TAG, "merge into ${file.name} failed, leaving file untouched", exception)
        }
    }
}
