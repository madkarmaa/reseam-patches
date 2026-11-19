// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.liveloop.analytics

import app.reseam.patch.klass
import app.reseam.patch.method

// UMP's obfuscated ConsentInformation implementation keeps the interface's method names.
private val consentInformation =
    klass("consentInformation") { implements("com.google.android.ump.ConsentInformation") }

// Home and Settings call this before showing the consent form; its success callback also reveals
// the Ad Settings row.
internal val requestConsentInfoUpdate = consentInformation.method("requestConsentInfoUpdate")
