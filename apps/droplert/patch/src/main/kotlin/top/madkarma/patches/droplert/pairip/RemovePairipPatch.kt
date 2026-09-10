// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.droplert.pairip

import app.reseam.patch.patch

val removePairip =
    patch("Remove Pairip") {
        description("Remove Pairip tamper protection")
        compatibleWith("com.shahzaman.pricetracker")

        execute {
            manifest.edit {
                findByAttribute("android:name", "com.google.android.play.core.common.PlayCoreDialogWrapperActivity").forEach { it.remove() }
                findByAttribute("android:name", "com.pairip.licensecheck.LicenseActivity").forEach { it.remove() }
                findByAttribute("android:name", "com.android.vending.CHECK_LICENSE").forEach { it.remove() }
            }
        }
    }
