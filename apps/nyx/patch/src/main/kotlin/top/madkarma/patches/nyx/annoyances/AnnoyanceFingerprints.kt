// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.nyx.annoyances

import app.reseam.patch.classTarget
import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

internal val showRatingDialog = method("showRatingDialog") {
    strings("Failed to show rate dialog")
}

private val reviewUi = klass("reviewUi") {
    enumConstants("NONE", "DIALOG", "IN_APP_REVIEW")
}

internal val noReviewUi = reviewUi.enumConstant("NONE")

// The dialog's callback is its only interface parameter.
internal val ratingCallback = classTarget("ratingCallback") {
    showRatingDialog.parameterTypes.mapNotNull { bytecode.findClass(it) }
        .singleOrNull { it.isInterface }
        ?: error("Expected exactly one rating-dialog callback interface")
}

internal val completeRating = method("completeRating") {
    inClass(ratingCallback)
    params(reviewUi.descriptor)
}
