// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.typinghero.premium

import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

// The one-time purchase entitlement among the two the app accepts as Premium.
internal const val PREMIUM_ENTITLEMENT = "legacy-premium"

internal val premiumEntitlements =
    method("premiumEntitlements") {
        strings(PREMIUM_ENTITLEMENT, "tester-premium")
    }

private val cacheFetchPolicy =
    klass("cacheFetchPolicy") {
        enumConstants("CACHE_ONLY", "FETCH_CURRENT")
    }

private val cacheOnly = cacheFetchPolicy.enumConstant("CACHE_ONLY")

// The app's customer info loader reads RevenueCat's cache first, then refreshes it.
private val loadCustomerInfo =
    method("loadCustomerInfo") {
        hasParam(cacheFetchPolicy.descriptor)
        accesses {
            owner(cacheOnly.owner)
            name(cacheOnly.name)
        }
    }

// Both loader steps go through this wrapper, which turns RevenueCat failures into null.
internal val fetchCustomerInfo =
    method("fetchCustomerInfo") {
        hasParam(cacheFetchPolicy.descriptor)
        calledBy(loadCustomerInfo)
    }

private val verificationResult =
    klass("verificationResult") { enumConstants("VERIFIED", "VERIFIED_ON_DEVICE") }

internal val verified = verificationResult.enumConstant("VERIFIED")

// RevenueCat's CustomerInfoFactory.buildCustomerInfo, which parses a backend response.
internal val buildCustomerInfo =
    method("buildCustomerInfo") {
        strings("first_seen")
        hasParam(verificationResult.descriptor)
    }

// RevenueCat's configuration refuses to start without the internet permission.
internal val configurePurchases =
    method("configurePurchases") {
        strings("Purchases requires INTERNET permission.")
    }

internal val mainDataSource =
    klass("originalDataSource") { enumConstants("MAIN", "OFFLINE_ENTITLEMENTS") }
        .enumConstant("MAIN")
