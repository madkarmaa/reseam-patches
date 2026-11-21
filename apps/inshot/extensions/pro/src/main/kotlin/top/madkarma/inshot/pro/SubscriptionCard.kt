// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("SubscriptionCard")
@file:Suppress("unused")

package top.madkarma.inshot.pro

import android.view.View
import android.widget.TextView
import app.reseam.runtime.Export

// Marks the card under its description view id, a key the app's resources own.
private val ATTRIBUTION_CARD = Any()

/**
 * Turns the Settings subscription [item] into a status card: its [descriptionId] text shows
 * [attribution] and its [arrowId] arrow is hidden.
 */
@Export
fun showAttribution(item: View?, descriptionId: Int, arrowId: Int, attribution: String) {
    if (item == null) return

    item.setTag(descriptionId, ATTRIBUTION_CARD)

    item.findViewById<TextView>(descriptionId)?.apply {
        text = attribution
        visibility = View.VISIBLE
    }
    item.findViewById<View>(arrowId)?.visibility = View.GONE
}

/** Whether [item] is the status card, which Settings must not open the subscription screen for. */
@Export
fun isAttributionCard(item: View?, descriptionId: Int): Boolean =
    item?.getTag(descriptionId) === ATTRIBUTION_CARD
