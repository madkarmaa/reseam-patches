// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.niagara

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object NiagaraSetup : ExtClass("top.madkarma.extensions.NiagaraSetup") {
    val skipToFavoritesSetup = static("skipToFavoritesSetup", Type.Context)
    val acceptTerms = static("acceptTerms", Type.Context)
    val refuseAnalytics = static("refuseAnalytics", Type.Context)
    val disableAnalyticsFlags = static("disableAnalyticsFlags", Type.Context)
}

internal object WeatherApi : ExtClass("top.madkarma.extensions.WeatherApi") {
    val create = static("create", Type.Context, returns = "top.madkarma.extensions.WeatherApi")
    val fetch = method("fetch", returns = Type.String)
    val now = method("now", returns = Type.Long)
}

internal object WeatherApiKeyField : ExtClass("top.madkarma.extensions.WeatherApiKeyField") {
    val attach = static("attach", Type.Object)
}
