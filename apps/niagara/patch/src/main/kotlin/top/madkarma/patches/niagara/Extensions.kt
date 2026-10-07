// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object NiagaraSetup : ExtClass("top.madkarma.extensions.NiagaraSetup") {
    val skipToFavoritesSetup by static(Type.Context)
    val acceptTerms by static(Type.Context)
    val refuseAnalytics by static(Type.Context)
    val disableAnalyticsFlags by static(Type.Context)
}

internal object WeatherApi : ExtClass("top.madkarma.extensions.WeatherApi") {
    val create by static(Type.Context, returns = "top.madkarma.extensions.WeatherApi")
    val fetch by method(returns = Type.String)
    val now by method(returns = Type.Long)
}

internal object WeatherApiKeyField : ExtClass("top.madkarma.extensions.WeatherApiKeyField") {
    val attach by static(Type.Object)
}
