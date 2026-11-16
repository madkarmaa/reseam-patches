// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.cipher.pro

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

internal const val FREE_TIER = "FREE"

// Checks the cached purchase, its expiry, the app's integrity, and the entitlement HMAC.
internal val cachedEntitlement =
    method("cachedEntitlement") {
        strings("cached_pro_expiry")
        returns(Type.Boolean)
    }

// Returns the cached tier, or "FREE" when the entitlement check fails.
internal val cachedTier =
    method("cachedTier") {
        inClass(klass(cachedEntitlement.owner))
        calls(cachedEntitlement)
        returns(Type.String)
    }

// Maps the stored settings to UserSettings and falls back to "FREE" without an active purchase.
internal val storedSettingsMapper =
    method("storedSettingsMapper") {
        strings("cached_navbar_compressed", FREE_TIER)
    }

private val userSettings = klass("userSettings") { strings("UserSettings(theme=") }

internal val userSettingsConstructor = userSettings.method("<init>")

// Flags a Pro license without a Google Play order or token, which the app asks to migrate.
internal val legacyProLicense =
    method("legacyProLicense") {
        inClass(userSettings)
        strings("GPA.")
    }

// isPro is the only boolean the legacy license check reads.
internal val isProField = legacyProLicense.field("isProField") { type(Type.Boolean) }

internal const val PLAY_ACCOUNT_LABEL = "Linked to your Google Play account across all your devices"
internal const val PRO_FEATURES_LABEL = "All Pro features unlocked • "
internal const val LIFETIME_ACCESS_LABEL = "Lifetime Access"

// The Membership & License card, which ends with the Google Play account line.
internal val membershipCard = method("membershipCard") { strings(PLAY_ACCOUNT_LABEL) }

// The Settings card that summarizes the active Pro plan.
internal val proSettingsCard =
    method("proSettingsCard") { strings(PRO_FEATURES_LABEL + LIFETIME_ACCESS_LABEL) }

// Reports a debugger, Xposed or LSPosed classes, or Frida and Xposed files in the process maps.
// The entitlement checks and PDF export require it to be false.
internal val hookedEnvironment =
    method("hookedEnvironment") {
        calls {
            owner("android.os.Debug")
            name("isDebuggerConnected")
        }
        returns(Type.Boolean)
    }
