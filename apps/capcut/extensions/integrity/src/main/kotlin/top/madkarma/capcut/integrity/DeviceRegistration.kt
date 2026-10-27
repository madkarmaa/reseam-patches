// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("DeviceRegistration") @file:Suppress("unused")

package top.madkarma.capcut.integrity

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import app.reseam.runtime.Export
import com.bytedance.frameworks.baselib.network.http.BaseRequestContext
import com.bytedance.retrofit2.client.Request
import com.ss.android.deviceregister.DeviceRegisterManager
import com.ss.android.deviceregister.OnResetListener
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.util.*

private const val TAG = "CapCutRegistration"
private lateinit var profileDirectory: File

@Volatile
private var installation: JSONObject? = null
private var resetRequested = false

@Export
@Synchronized
fun initialize(context: Context?) {
    if (context == null || installation != null) return

    profileDirectory = File(context.noBackupFilesDir, "reseam-registration")
    check(profileDirectory.isDirectory || profileDirectory.mkdirs()) {
        "Cannot create the registration profile directory"
    }

    installation = updateProfile { it }
}

// All app processes share one profile, and Android backups must not restore another installation's IDs.
@Synchronized
private fun updateProfile(update: (JSONObject) -> JSONObject): JSONObject {
    RandomAccessFile(File(profileDirectory, "installation.lock"), "rw").channel.use { channel ->
        channel.lock().use {
            val file = AtomicFile(File(profileDirectory, "installation.json"))

            val stored = try {
                JSONObject(String(file.readFully(), Charsets.UTF_8))
            } catch (missing: FileNotFoundException) {
                JSONObject().apply {
                    put("openudid", UUID.randomUUID().toString().replace("-", "").take(16))
                    for (key in listOf("clientudid", "google_aid", "cdid")) put(
                        key, UUID.randomUUID().toString()
                    )
                    put("registered", false)
                }
            }

            val updated = update(stored)

            val output = file.startWrite()
            try {
                output.write(updated.toString().toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (failure: Throwable) {
                file.failWrite(output)
                throw failure
            }

            return updated
        }
    }
}

@Export
@Synchronized
fun resetIfNeeded() {
    if (resetRequested || checkNotNull(installation).optBoolean("registered")) return
    resetRequested = true

    val childMode = DeviceRegisterManager.isChildMode()
    check(DeviceRegisterManager.clearWhenSwitchChildMode(childMode)) {
        "Clearing the previous device registration failed"
    }

    DeviceRegisterManager.resetDidWhenSwitchChildMode(childMode, 10_000, object : OnResetListener {
        override fun onDidUpdate(deviceId: String?, installId: String?) {
            if (deviceId.isNullOrEmpty() || deviceId == "0" || installId.isNullOrEmpty() || installId == "0") {
                Log.e(TAG, "The registration server did not return valid identifiers")
                return
            }

            installation = updateProfile { it.put("registered", true) }
            Log.i(TAG, "The SDK accepted a registration for this installation")
        }

        override fun onTimeout() {
            Log.e(TAG, "Device registration timed out; the SDK will retry")
        }
    })
}

@Export
fun preparePayload(serialized: String?, header: JSONObject?): String {
    val payload = JSONObject(checkNotNull(serialized))
    val outgoing = payload.getJSONObject("header")

    val registrationHeader = checkNotNull(header)
    val profile = checkNotNull(installation) { "Registration was not initialized" }

    for (key in listOf("openudid", "clientudid", "google_aid", "cdid")) {
        val value = profile.getString(key)
        outgoing.put(key, value)
        registrationHeader.put(key, value)
    }

    // Let the server resolve this profile instead of reusing a rejected server ID.
    for (key in listOf("device_id", "device_id_str", "install_id", "install_id_str")) {
        outgoing.remove(key)
        registrationHeader.remove(key)
    }

    return payload.toString()
}

@Suppress("UseKtx")
@Export
fun isRegistration(url: String?): Boolean =
    url != null && Uri.parse(url).path == "/service/2/device_register/"

@Export
fun excludeAppHeaders(request: Request?): Boolean {
    if (request == null || !isRegistration(request.url)) return false

    val context = request.extraInfo as? BaseRequestContext
        ?: error("Device registration has no network request context")

    context.bypassCookie = true
    return true
}
