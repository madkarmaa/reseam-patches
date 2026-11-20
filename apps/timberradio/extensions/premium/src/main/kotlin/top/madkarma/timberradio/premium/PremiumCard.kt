// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("PremiumCard")
@file:Suppress("unused")

package top.madkarma.timberradio.premium

import android.view.View
import android.widget.TextView
import app.reseam.runtime.Export

/**
 * Turns the Premium card into a status card: [subscribeButton] shows [attribution], and neither it
 * nor [card] opens the purchase screen.
 */
@Export
fun showAttribution(card: View, subscribeButton: TextView, attribution: String) {
    card.setOnClickListener(null)
    card.isClickable = false

    subscribeButton.setOnClickListener(null)
    subscribeButton.isClickable = false
    subscribeButton.isAllCaps = false
    subscribeButton.text = attribution
}
