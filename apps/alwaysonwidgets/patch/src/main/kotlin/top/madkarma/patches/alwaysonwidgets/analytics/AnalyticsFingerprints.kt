// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.alwaysonwidgets.analytics

import app.reseam.patch.Type
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method

// Gathers UMP consent and forwards it to Firebase Analytics as consent mode.
private val consentManager = klass("consentManager") { strings("UMP_consentModeValues") }

internal val consentManagerConstructor = consentManager.method("<init>")

// Whether an AdMob app ID is configured; without one the manager blocks ads instead of asking for
// consent.
internal val adsConfigured = consentManager.fieldOfType(Type.Boolean)
