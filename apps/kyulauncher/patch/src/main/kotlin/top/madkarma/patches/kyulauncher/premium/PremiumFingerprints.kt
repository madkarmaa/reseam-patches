// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.kyulauncher.premium

import app.reseam.patch.klass
import app.reseam.patch.method

private val purchaseManager =
    klass("purchaseManager") {
        strings("Premium price loaded: ")
    }

// Every Premium check reads the state this setter writes; it also persists it in the preferences.
internal val setPremium =
    method("setPremium") {
        inClass(purchaseManager)
        strings("is_premium")
    }

// Called at startup, after the Premium state is restored from the preferences.
internal val connectBilling =
    method("connectBilling") {
        inClass(purchaseManager)
        strings("Please provide a valid listener for purchases updates.")
    }
