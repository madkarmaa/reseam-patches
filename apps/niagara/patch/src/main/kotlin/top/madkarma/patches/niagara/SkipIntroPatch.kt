package top.madkarma.patches.niagara

import app.reseam.patch.after
import app.reseam.patch.appEntry
import app.reseam.patch.patch

val skipIntro = patch("Skip intro") {
    description("Skips the promo and terms screens, landing directly on setup.")
    compatibleWith("bitpit.launcher")

    val acceptTermsOption = boolOption(
        "acceptTerms",
        title = "Accept terms",
        description = "Marks the terms as accepted, skipping the terms screen.",
        default = true,
    )

    val skipToFavoritesOption = boolOption(
        "skipToFavorites",
        title = "Skip to favorites",
        description = "Jumps the onboarding flow straight to the favorites setup step.",
        default = true,
    )

    execute {
        if (options[acceptTermsOption]) {
            appEntry.after {
                call(NiagaraSetup.acceptTerms, thisObject)
            }
        }

        if (options[skipToFavoritesOption]) {
            appEntry.after {
                call(NiagaraSetup.skipToFavoritesSetup, thisObject)
            }
        }
    }
}
