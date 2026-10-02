// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.alltrails.peak

import app.reseam.patch.klass
import app.reseam.patch.method

private val appUser = klass("appUser") {
    strings("User [remoteId=")
}

// The user model keeps its readable getter names through obfuscation.
internal val userIsPro = appUser.method("isPro")
internal val userSubscriptionTier = appUser.method("getSubscriptionTier")
