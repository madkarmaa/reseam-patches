// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.lightroom.premium

import app.reseam.patch.Type
import app.reseam.patch.dex.AccessFlags
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods

// The entitlement holder checks the CC Standard plan code next to its subscription predicates.
internal val entitlements = klass("entitlements") { strings("PA-1517") }

// The holder's only private check. The account status consults it before upgrading free accounts
// to Subscription; it reads the Lightroom mobile app entitlement, which also grants premium tools.
private val subscriptionUpgrade =
    method("subscriptionUpgrade") {
        inClass(entitlements)
        flags(AccessFlags.PRIVATE)
        returns(Type.Boolean)
    }

// 12.0: the entitlement lives in a state flow and the private check calls its public getter.
internal val lightroomMobileEntitlement =
    method("lightroomMobileEntitlement") {
        inClass(entitlements)
        calledBy(subscriptionUpgrade)
    }

// 11.6: the private check reads the entitlement directly from a static field.
internal val storedEntitlementCheck =
    method("storedEntitlementCheck") {
        inClass(entitlements)
        flags(AccessFlags.PRIVATE)
        returns(Type.Boolean)
        accesses { type(Type.Boolean) }
    }

internal val lightroomMobileFlag =
    storedEntitlementCheck.field("lightroomMobileFlag") { type(Type.Boolean) }

// Decides whether the first launch after sign-in opens the trial paywall: the sign-in flag plus
// data consent. Its only caller marks the upsell as shown and skips it when this returns false.
internal val firstLaunchUpsellEligible =
    method("firstLaunchUpsellEligible") {
        returns(Type.Boolean)
        strings("DATA_CONSENT_GIVEN")
        accesses { name("IS_FIRST_LAUNCH_AUTHENTICATION") }
    }

// Picks the sidebar's subscription line. It reports "You're subscribed" only when both the
// Lightroom mobile and the cloud storage entitlements are on.
internal val drawerSubscriptionStatus =
    method("drawerSubscriptionStatus") {
        accesses { name("Trial_Expired") }
        accesses { name("Subscription_Expired") }
        calls {
            owner(entitlements.descriptor)
            returns(Type.Boolean)
        }
    }

// The status types are Kotlin objects that name themselves. The sidebar's UI state mirrors them, so
// "Subscribed" names two objects; the status one implements the type the method returns.
private val subscribedStatusName =
    methods("subscribedStatusName") {
            name("toString")
            strings("Subscribed")
        }
        .single { drawerSubscriptionStatus.returnType in method.classDef.interfaces }

internal val subscribedStatus =
    drawerSubscriptionStatus.field("subscribedStatus") { type(subscribedStatusName.owner) }

// Supplies the Premium Features screen and reports its premium-user analytics events.
private val premiumFeaturesSheet =
    klass("premiumFeaturesSheet") { strings("PremiumUser:HomeScreen:ExitSheet") }

// The screen's own premium check, which also requires the cloud storage entitlement. It picks the
// "Your Premium plan" header and hides the trial footer and buttons.
internal val premiumFeaturesSheetPremium =
    method("premiumFeaturesSheetPremium") {
        inClass(premiumFeaturesSheet)
        returns(Type.Boolean)
        calls {
            owner(entitlements.descriptor)
            returns(Type.Boolean)
        }
    }
