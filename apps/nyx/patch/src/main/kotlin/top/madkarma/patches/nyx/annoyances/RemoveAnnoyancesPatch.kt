// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.nyx.annoyances

import app.reseam.patch.className
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.nyx.annoyances.bindings.Rating
import top.madkarma.patches.nyx.NyxPatchStrings

val removeAnnoyances =
    patch(NyxPatchStrings.removeAnnoyancesName) {
        description(NyxPatchStrings.removeAnnoyancesDescription)
        compatibleWith("com.awedea.nyx")

        execute {
            showRatingDialog.replace {
                Rating.skip(
                    paramOfType(ratingCallback.descriptor),
                    string(className(completeRating.owner)),
                    string(completeRating.name),
                    staticField(noReviewUi),
                )
                returnVoid()
            }
            log.info(
                "Annoyances: ${showRatingDialog.descriptor} skips the rating popup and completes its callback.",
            )

            val rateId = resources.id("id", "menu_rate") ?: error("Rate drawer entry missing")
            resources.editXml("layout", "nav_view_body") {
                val rateButton =
                    findByAttribute("android:id", "@0x${rateId.toString(16)}").singleOrNull()
                        ?: error("Expected exactly one Rate button in the navigation drawer")

                rateButton["android:visibility"] = "gone"
            }
            log.info("Annoyances: layout/nav_view_body hides the Rate drawer entry.")
        }
    }
