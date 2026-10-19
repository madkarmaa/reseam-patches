// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.cloudflare.unlimited

import app.reseam.patch.enumConstant
import app.reseam.patch.klass
import app.reseam.patch.method

private val accountData = klass("accountData") {
    strings("AccountData(id=")
}

// The primary constructor checks accountType; the default-argument constructor delegates to it.
internal val accountConstructor = accountData.method("<init>") {
    strings("accountType")
}

internal val accountTypes = klass("accountTypes") {
    enumConstants("UNLIMITED", "TEAM")
}

internal val unlimitedAccount = accountTypes.enumConstant("UNLIMITED")
internal val teamAccount = accountTypes.enumConstant("TEAM")

private val accountSettings = klass("accountSettings") {
    strings("AccountSettingsState(appState=")
}

internal val accountSettingsConstructor = accountSettings.method("<init>")
