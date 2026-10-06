// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.googlephotos

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object GmsCoreSupport : ExtClass("top.madkarma.googlephotos.gms.GmsCoreSupport") {
    val check by static(Type.Activity, Type.String, Type.String)
    val readAccounts by static(Type.Context, Type.String, returns = "[Landroid/accounts/Account;")
    val initialize by static("java.util.concurrent.Callable")
    val getAccountRefreshExecutor by static(returns = "java.util.concurrent.ExecutorService")
    val withPackageName by static(Type.Context, Type.String, returns = Type.Context)
    val rememberAccountsListener by static("android.accounts.OnAccountsUpdateListener")
    val onAccountPermissionResult by static(
        Type.Activity, Type.Int, "[Ljava/lang/String;", "[I", Type.String, returns = Type.Boolean
    )
}

internal object PixelXlIdentity : ExtClass("top.madkarma.googlephotos.spoof.PixelXlIdentity") {
    val get by static(Type.String, returns = Type.String)
    val getWithDefault by static(Type.String, Type.String, returns = Type.String, name = "get")
}
