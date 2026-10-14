// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.picsart.ultra

import android.util.Log
import app.reseam.runtime.Export

object Ultra {
    private var logged = false

    @Export
    @JvmStatic
    fun isUserSubscribed(): Boolean = true

    @Export
    @JvmStatic
    fun accessLevel(): Int = Int.MAX_VALUE

    @Export
    @JvmStatic
    @Synchronized
    fun addPremiumPermissions(original: List<String?>?): List<String?>? {
        try {
            val permissions = LinkedHashSet<String?>()
            if (original != null) permissions.addAll(original)

            permissions.add("premium_tools_standard")
            permissions.add("premium_tools_ai")

            if (!logged) {
                Log.i("PicsartUltra", "Local Ultra entitlement applied")
                logged = true
            }

            return ArrayList(permissions)
        } catch (exception: RuntimeException) {
            Log.w("PicsartUltra", "Could not normalize permissions", exception)
            return original
        }
    }
}
