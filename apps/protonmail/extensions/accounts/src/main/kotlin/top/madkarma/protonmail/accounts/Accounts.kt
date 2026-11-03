// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Accounts")

package top.madkarma.protonmail.accounts

import app.reseam.runtime.Export

@Export
@Synchronized
fun install(diagnostics: Boolean) {
    System.loadLibrary("ProtonAccounts")
    check(installNative(diagnostics)) {
        "Proton Mail: installing the native account-limit hook failed; enable diagnostics for details"
    }
}

private external fun installNative(diagnostics: Boolean): Boolean
