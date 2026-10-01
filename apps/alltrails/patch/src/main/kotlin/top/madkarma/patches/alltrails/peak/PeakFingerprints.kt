// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.alltrails.peak

import app.reseam.patch.klass
import app.reseam.patch.method

// Backend user model (Gson User.java): toString keys plus @SerializedName
// keys pin it; the kept getter names resolve off the found class.
val appUser = klass("alltrails user") {
    strings("User [remoteId=")
}

val userIsPro = method("user is pro") {
    inClass(appUser)
    name("isPro")
}

// Raw backend tier string on the user model. Declared once (User);
// "peak" is the backend serial.
val userSubscriptionTier = method("user subscription tier") {
    inClass(appUser)
    name("getSubscriptionTier")
}
