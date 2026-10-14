// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.googlephotos.spoof

import android.os.SystemProperties
import app.reseam.runtime.Export

object PixelXlIdentity {
    private const val FINGERPRINT =
        "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys"

    @Export
    @JvmStatic
    fun get(key: String): String = property(key) ?: SystemProperties.get(key)

    @Export(name = "getWithDefault")
    @JvmStatic
    fun get(key: String, fallback: String?): String? =
        property(key) ?: SystemProperties.get(key, fallback)

    private fun property(key: String): String? {
        when (key) {
            "ro.build.id" -> return "QP1A.191005.007.A3"
            "ro.build.version.incremental" -> return "5972272"
            "ro.build.product" -> return "marlin"
            "ro.product.model.marketname" -> return "Pixel XL"
            "ro.build.fingerprint", "ro.vendor.build.fingerprint", "ro.system.build.fingerprint", "ro.bootimage.build.fingerprint", "ro.odm.build.fingerprint" -> return FINGERPRINT
        }

        var product = key
        for (partition in arrayOf("system", "vendor", "odm")) {
            val prefix = "ro.product.$partition."

            if (product.startsWith(prefix)) product =
                "ro.product." + product.substring(prefix.length)
        }

        return when (product) {
            "ro.product.brand" -> "google"
            "ro.product.manufacturer" -> "Google"
            "ro.product.device", "ro.product.name" -> "marlin"
            "ro.product.model" -> "Pixel XL"
            else -> null
        }
    }
}
