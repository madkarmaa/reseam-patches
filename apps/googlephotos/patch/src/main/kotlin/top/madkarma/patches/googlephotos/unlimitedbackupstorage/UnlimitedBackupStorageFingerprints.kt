// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.googlephotos.unlimitedbackupstorage

import app.reseam.patch.*
import app.reseam.patch.dex.Opcode

internal val initializePixelFeatures = method("initializePixelFeatures") {
    strings("com.google.android.apps.photos.NEXUS_PRELOAD")
}

private val pixelOfferDetails = klass("pixelOfferDetails") {
    strings("PixelOfferDetail{pixelModelName=")
}

private val pixelOfferReaders = methods("pixelOfferReaders") {
    calls(pixelOfferDetails.method("<init>") { hasParam(Type.String) })
}

// The model/device checks have no diagnostic strings; their class exposes the retail-mode key.
private val pixelDeviceChecks = method("pixelDeviceChecks") {
    inClass(klass("pixelDeviceChecksClass") {
        strings(
            "com.google.android.apps.photos.retaildemo.is_pixel_retail_mode", "device_demo_mode"
        )
    })
    calls { owner("java.lang.Object"); name("getClass"); params() }
}

private val pixelIdentityHash = method("pixelIdentityHash") {
    calledBy(pixelDeviceChecks)
    params(Type.String)
    returns(Type.Long)
}

// The hardware and model checks identify the device with the same hash function.
private val pixelHardwareChecks = method("pixelHardwareChecks") {
    calledBy(pixelDeviceChecks)
    calls(pixelIdentityHash)
}

// The shared request device-info builder has no strings. Its thread check and protobuf builder
// calls narrow the search before any instructions are decoded.
private val requestDeviceInfo = methods("requestDeviceInfo") {
    calls(method("requireBackgroundThread") {
        strings("Must be called on a background thread")
    })
    calls { name("createBuilder"); params() }
    params()
}

internal val deviceIdentityReads = listOf(
    method("pixelBackupSettings") {
        strings("backup.settings_fixer")
    }.points("pixelBackupIdentityReads") {
        buildIdentityRead()
    },

    method("pixelUploadRequest") {
        strings("Upload Media Crc32C Mismatch")
    }.points("pixelUploadIdentityReads") {
        buildIdentityRead()
    },

    pixelOfferReaders.points("pixelOfferIdentityReads") {
        buildIdentityRead()
    },

    pixelDeviceChecks.points("pixelDeviceIdentityReads") {
        buildIdentityRead()
    },

    pixelHardwareChecks.points("pixelHardwareIdentityReads") {
        buildIdentityRead()
    },

    requestDeviceInfo.points("requestDeviceIdentityReads") {
        buildIdentityRead()
    },

    methods("deviceUserAgents") {
        strings(" (Linux; U; Android ")
    }.points("userAgentIdentityReads") {
        buildIdentityRead()
    },

    method("deviceBuildSignals") {
        strings("Null baseOs")
    }.points("deviceSignalIdentityReads") {
        buildIdentityRead()
    },

    method("notificationDeviceInfo") {
        strings("device_country", "Failed to get notification channels from Android.")
    }.points("notificationIdentityReads") {
        buildIdentityRead()
    },

    methods("growthDeviceInfo") {
        inClass(klass("growthDeviceInfoClass") {
            strings("Not syncing signed out user - token is null - can happen if device just started")
        })
    }.points("growthIdentityReads") {
        buildIdentityRead()
    },
)

private fun PointMatch.buildIdentityRead() {
    opcode(Opcode.SGET_OBJECT)
    field { owner("android.os.Build"); type(Type.String) }
}
