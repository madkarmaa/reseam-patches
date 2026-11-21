// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.swiftkey.telemetry

import app.reseam.patch.klass
import app.reseam.patch.method

// Every telemetry event is serialized into the on-disk upload queue here before it is sent to
// telemetry.api.swiftkey.com.
internal val queueTelemetryEvent =
    method("queueTelemetryEvent") {
        params("Lorg/apache/avro/generic/GenericRecord;")
        strings("writing")
    }

// Before each upload, the queued event files move to a staging folder the uploader sends from.
internal val stageTelemetryUpload =
    method("stageTelemetryUpload") { strings("lumberjack", "staging") }

internal val initializeAdjust = klass("com.adjust.sdk.Adjust").method("initSdk")
