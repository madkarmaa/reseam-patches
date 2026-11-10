// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.atvtools.premium

import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

private val nativeStartup =
    method("nativeStartup") {
        name("onCreate")
        strings("a")
        calls {
            owner("java.lang.System")
            name("loadLibrary")
        }
    }

internal val applicationInitializer = klass(nativeStartup.owner).method("<clinit>")

private val billingManager =
    klass("billingManager") {
        strings("Please provide a valid listener for purchases updates.")
    }

internal val billingConstructor = billingManager.method("<init>")

internal val premiumState = billingManager.fieldOfType("kotlinx.coroutines.flow.StateFlow")

private val purchase =
    klass("purchase") {
        strings("Purchase. Json: ")
    }

internal val purchaseConstructor = purchase.method("<init>")

internal val premiumScreen = klass("dev.vodik7.atvtools.PremiumActivity").method("onCreate")
