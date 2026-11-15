// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("AccountChooser")
@file:Suppress("unused")

package top.madkarma.linkboy.extensions

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import app.reseam.runtime.Export

private const val CHOOSE_ACCOUNT = "com.google.android.gms.common.account.CHOOSE_ACCOUNT"

/**
 * Replaces Play Services' account picker with the system chooser, which makes the chosen account
 * visible to the app. GmsCore accounts are hidden from apps until then.
 */
@Export
fun systemChooser(intent: Intent): Intent {
    if (intent.action != CHOOSE_ACCOUNT) return intent

    @Suppress("DEPRECATION")
    val selectedAccount = intent.getParcelableExtra<Account>("selectedAccount")
    return AccountManager.newChooseAccountIntent(
        selectedAccount,
        null,
        intent.getStringArrayExtra("allowableAccountTypes"),
        null,
        null,
        null,
        null,
    )
}
