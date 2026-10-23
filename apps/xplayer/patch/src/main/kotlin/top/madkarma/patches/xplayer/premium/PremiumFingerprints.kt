// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.xplayer.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val billingStartup = method("billingStartup") {
    strings("Pro_UpgPre251")
}

internal val booleanPreference = method("booleanPreference") {
    calledBy(billingStartup)
    calls {
        owner("android.content.SharedPreferences")
        name("getBoolean")
    }
}

private val purchaseScreen = klass("purchaseScreen") {
    strings("clLifetime")
}

private val purchaseStateChanged = method("purchaseStateChanged") {
    inClass(purchaseScreen)
    strings("state")
}

private val cachedPremium = method("cachedPremium") {
    calledBy(purchaseStateChanged)
    returns(Type.Boolean)
}

internal val updateCachedPremium = method("updateCachedPremium") {
    inClass(klass(cachedPremium.owner))
    calledBy(billingStartup)
    hasParam(Type.Boolean)
}

internal val purchaseStateConstructor = method("purchaseStateConstructor") {
    inClass(klass(cachedPremium.owner))
    name("<init>")
    params()
}
