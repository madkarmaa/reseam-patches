// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Accounts")

package top.madkarma.protonmail.accounts

import app.reseam.runtime.Export

@Export
@Synchronized
fun install() {
    System.loadLibrary("ProtonAccounts")
    check(installNative()) {
        "Proton Mail: installing the native account-limit hook failed; see ProtonAccounts logcat"
    }
}

private external fun installNative(): Boolean
