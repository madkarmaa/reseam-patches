// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.timberradio.telemetry

import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

// Categories of data the app uploads to its server. STATISTICS covers the now-playing radar and
// trending reports; the others are user backups.
internal val uploadType = klass("uploadType") { enumConstants("STATISTICS", "FAVORITES") }

internal val statisticsUpload = uploadType.enumConstant("STATISTICS")

// Every upload to the app's server goes through this method.
internal val uploadDataToServer =
    method("uploadDataToServer") {
        strings(" DATA_UPLOAD")
        hasParam(uploadType.descriptor)
    }

// The application initializes the AdMob SDK here at startup.
internal val initializeAds =
    method("initializeAds") {
        calls {
            owner("com.google.android.gms.ads.MobileAds")
            name("initialize")
        }
    }
