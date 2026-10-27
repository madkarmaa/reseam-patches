// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Profile") @file:Suppress("unused")

package top.madkarma.capcut.premium

import android.util.Log
import app.reseam.runtime.Export
import org.json.JSONException
import org.json.JSONObject

@Export
fun hideSubscriptionEntrance(configuration: JSONObject?) {
    if (configuration == null || !configuration.has("vip_entrance_enable")) return

    configuration.put("vip_entrance_enable", false)
    Log.i("CapCutPremium", "Hidden the profile subscription chip")
}

@Export
fun subscriptionTouchConfiguration(name: String?, configuration: String?): String? {
    if (name != "cc_subscription_entry_press_opt") return configuration

    val settings = try {
        JSONObject(configuration ?: "{}")
    } catch (exception: JSONException) {
        Log.w("CapCutPremium", "Could not read subscription touch configuration", exception)
        JSONObject()
    }

    settings.put("enable_press_activation", false)
    settings.put("enable_hit_slop_expansion", false)

    Log.i("CapCutPremium", "Disabled subscription press activation and expanded touch targets")
    return settings.toString()
}

@Export
fun withAttribution(translations: String?, attribution: String): String? {
    if (translations == null || !translations.contains("mob_me_help_center")) return translations

    return try {
        val strings = JSONObject(translations)
        if (!strings.has("mob_me_help_center")) return translations

        strings.put("mob_me_help_center", attribution)

        Log.i("CapCutPremium", "Replaced the profile Help Center label with the shared attribution")
        strings.toString()
    } catch (exception: JSONException) {
        Log.w("CapCutPremium", "Could not read profile translations", exception)
        translations
    }
}
