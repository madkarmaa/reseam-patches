// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.smartlauncher.lifetime

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

internal const val LIFETIME_PREFERENCE = "rvrTasMode2"

internal val fullLicenseRow = method("fullLicenseRow") {
    strings("SL6")
}

internal val lifetimePreferenceInitializer = method("lifetimePreferenceInitializer") {
    strings(LIFETIME_PREFERENCE)
    name("<clinit>")
}

internal val readPreference = method("readPreference") {
    strings(" is not a boolean")
}

internal val writePreference = method("writePreference") {
    inClass(klass(readPreference.owner))
    returns(Type.Void)
    calls {
        owner($$"android.content.SharedPreferences$Editor")
        name("putBoolean")
    }
}

internal val preferenceName = method("preferenceName") {
    inClass(klass(readPreference.owner), inherited = true)
    name("name")
}
