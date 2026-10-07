// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.googlephotos.unlimitedbackupstorage

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.dex.stringValue
import app.reseam.patch.patch
import app.reseam.patch.replaceAllStrings
import top.madkarma.patches.googlephotos.GOOGLE_PHOTOS
import top.madkarma.patches.googlephotos.PixelXlIdentity

private val ORIGINAL_PIXEL_FEATURES = setOf(
    "$GOOGLE_PHOTOS.NEXUS_PRELOAD",
    "$GOOGLE_PHOTOS.nexus_preload",
    "com.google.android.feature.PIXEL_EXPERIENCE",
    "$GOOGLE_PHOTOS.PIXEL_PRELOAD",
    "$GOOGLE_PHOTOS.PIXEL_2016_PRELOAD",
)

private val NEWER_PIXEL_FEATURE = Regex(
    "com\\.google\\.android\\.feature\\.PIXEL_20(?:1[7-9]|[2-9][0-9])(?:_MIDYEAR)?_EXPERIENCE|" + "com\\.google\\.android\\.apps\\.photos\\.PIXEL_20(?:1[7-9]|[2-9][0-9])(?:_MIDYEAR)?_PRELOAD",
)

private val PIXEL_XL_FIELDS = mapOf(
    "BRAND" to "google",
    "MANUFACTURER" to "Google",
    "BOARD" to "marlin",
    "DEVICE" to "marlin",
    "PRODUCT" to "marlin",
    "HARDWARE" to "marlin",
    "MODEL" to "Pixel XL",
    "ID" to "QP1A.191005.007.A3",
    "DISPLAY" to "QP1A.191005.007.A3",
    "FINGERPRINT" to "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys",
    "TAGS" to "release-keys",
    "TYPE" to "user",
    "USER" to "android-build",
)

val unlimitedBackupStorage = patch("Unlimited backup storage") {
    description("Unlocks unlimited Google Photos backup storage through Google's Pixel XL offer by spoofing the device as a Pixel XL.")
    compatibleWith(GOOGLE_PHOTOS)

    execute {
        val features =
            initializePixelFeatures.method.instructions.mapNotNull { it.stringValue }.toSet()

        val enabled = features.intersect(ORIGINAL_PIXEL_FEATURES)

        val disabled =
            features.filter { it !in ORIGINAL_PIXEL_FEATURES && NEWER_PIXEL_FEATURE.matches(it) }

        check(enabled.isNotEmpty() && disabled.isNotEmpty()) { "Unsupported Pixel feature catalogue" }

        enabled.forEach { initializePixelFeatures.replaceAllStrings(it, "android.hardware.wifi") }
        disabled.forEach { initializePixelFeatures.replaceAllStrings(it, "dummy") }

        // Override values at their read sites; Android 17 refuses reflective writes to final Build fields.
        val identityReads = deviceIdentityReads.flatMap { target ->
            target.all.filter { it.field().name in PIXEL_XL_FIELDS }.also {
                check(it.isNotEmpty()) { "No Google Photos device identity reads matched ${target.debugName}" }
            }
        }

        for (read in identityReads) {
            val value = PIXEL_XL_FIELDS.getValue(read.field().name)
            read.captureAs("identity", Type.String)
                .after { capture("identity").assign(string(value)) }
        }

        val properties =
            bytecode.redirectCalls("android.os.SystemProperties", "get", PixelXlIdentity.get)

        val propertiesWithDefault = bytecode.redirectCalls(
            "android.os.SystemProperties", "get", PixelXlIdentity.getWithDefault,
        )

        log.info("Unlimited backup storage: spoofed ${identityReads.size} Build reads, enabled ${enabled.size}, disabled ${disabled.size} features; redirected ${properties + propertiesWithDefault} property reads.")
    }
}
