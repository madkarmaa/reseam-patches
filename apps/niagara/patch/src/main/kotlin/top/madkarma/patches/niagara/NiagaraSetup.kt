package top.madkarma.patches.niagara

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object NiagaraSetup : ExtClass("top.madkarma.extensions.NiagaraSetup") {
    val skipToFavoritesSetup = static("skipToFavoritesSetup", Type.Context)
    val acceptTerms = static("acceptTerms", Type.Context)
    val refuseAnalytics = static("refuseAnalytics", Type.Context)
    val disableAnalyticsFlags = static("disableAnalyticsFlags", Type.Context)
}
