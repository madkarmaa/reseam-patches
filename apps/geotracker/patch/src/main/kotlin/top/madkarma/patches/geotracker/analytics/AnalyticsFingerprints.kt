// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.geotracker.analytics

import app.reseam.patch.*

// The privacy switches the app hands to Firebase Analytics, Crashlytics and Performance.
private val dataCollection =
    klass("dataCollection") {
        strings("DataCollection(usage=")
    }

internal val dataCollectionConstructor = dataCollection.method("<init>")

private val settings =
    klass("settings") {
        strings("Settings(record=", ", collectStats=")
    }

// Builds the defaults, the stored settings, and every update to them, including the usage
// statistics switch that accepting the terms turns on.
internal val settingsConstructor = settings.method("<init>")

// Declares the settings screen's sections; Privacy holds the data collection switches. R8 keeps
// only
// the first sections in fields, so the declaration names them.
internal val settingsSectionInitializer =
    method("settingsSectionInitializer") {
        strings("Privacy", "Advanced")
        name("<clinit>")
    }

// Restores the last opened section by name from the section entries, which the settings screen also
// iterates to build its tabs.
private val savedSettingsSection =
    method("savedSettingsSection") {
        returns(settingsSectionInitializer.owner)
        params()
        calls {
            owner("java.lang.Enum")
            name("name")
        }
    }

internal val settingsSectionEntries =
    savedSettingsSection.field("settingsSectionEntries") { owner(settingsSectionInitializer.owner) }

internal val settingsSectionEntriesConstructor =
    method("settingsSectionEntriesConstructor") {
        inClass(klass(settingsSectionEntries.type))
        name("<init>")
    }

// Mapbox keeps its SDK names.
private val mapTelemetry = klass("com.mapbox.maps.module.telemetry.MapTelemetryImpl")

internal val mapTelemetryConstructors =
    mapTelemetry.methods("mapTelemetryConstructors") {
        name("<init>")
    }

internal val disableTelemetrySession = mapTelemetry.method("disableTelemetrySession")

internal val setUserTelemetryRequestState = mapTelemetry.method("setUserTelemetryRequestState")

// Gates map load and performance events.
internal val shouldSendMapEvents = mapTelemetry.method("shouldSendEvents")

internal val mapTurnstileEvent =
    mapTelemetry.method("onAppUserTurnstileEvent") {
        params("com.mapbox.maps.module.telemetry.MapTelemetryMetadata")
    }

// HMS keeps its SDK names; these are the event reporters of its analytics client.
internal val huaweiAnalyticsEvents =
    klass("com.huawei.hms.support.hianalytics.HiAnalyticsUtils").methods("huaweiAnalyticsEvents") {
        returns(Type.Void)
        custom { name.startsWith("on") }
    }
