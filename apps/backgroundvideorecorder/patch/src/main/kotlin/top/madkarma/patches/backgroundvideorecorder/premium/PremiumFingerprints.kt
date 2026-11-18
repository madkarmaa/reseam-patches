// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.backgroundvideorecorder.premium

import app.reseam.patch.*

private val adMode =
    klass("adMode") {
        enumConstants("NO_ADS", "ALL_ADS")
    }

internal val noAds = adMode.enumConstant("NO_ADS")
private val allAds = adMode.enumConstant("ALL_ADS")

private val purchaseInfo =
    klass("purchaseInfo") {
        strings("PurchaseInfo(id=")
    }

internal val purchaseInfoConstructors = purchaseInfo.methods("purchaseInfoConstructors") { name("<init>") }

private val billingManager =
    klass("billingManager") {
        strings("subs_single")
    }

internal val billingManagerConstructor = billingManager.method("<init>")

// Billing setup failures and empty product lists call this to show ads again.
internal val forceAllAds =
    method("forceAllAds") {
        inClass(billingManager)
        accesses {
            owner(allAds.owner)
            name(allAds.name)
        }
        params()
        returns(Type.Void)
    }

// The reset writes only the manager's ad-mode flow; the purchase map flow shares its type.
internal val adModeUpdates = forceAllAds.field("adModeUpdates") { owner(billingManager.descriptor) }

// The constructor creates its ad-mode and purchase flows with this factory.
internal val stateFlowFactory =
    method("stateFlowFactory") {
        calledBy(billingManagerConstructor)
        returns(adModeUpdates.type)
    }
