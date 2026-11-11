// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.premium

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

private val premiumTier = klass("com.truecaller.premium.data.tier.PremiumTierType")

internal val premiumTierGetter =
    method("premiumTierGetter") {
        strings("premiumLevel")
        returns(premiumTier.descriptor)
    }

internal val isPremiumActive =
    method("isPremiumActive") {
        strings("isPremiumExpired")
        custom { indexOfFirstString("isInGracePeriod") == null }
    }

private val premiumFeature = klass("com.truecaller.premium.data.feature.PremiumFeature")

private val noAds = premiumFeature.field("NO_ADS")

internal val featureIncluded =
    method("featureIncluded") {
        strings("disabledPremiumFeatures")
        params(premiumFeature.descriptor, Type.Boolean)
    }

private val adFreeEntitlement =
    method("adFreeEntitlement") {
        inClass(klass(featureIncluded.owner))
        accesses { name(noAds.name) }
    }

// The ad-free check calls both entitlement and catalog checks. Only the catalog check takes one
// feature.
internal val featureOffered =
    method("featureOffered") {
        calledBy(adFreeEntitlement)
        params(premiumFeature.descriptor)
    }
