// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: AGPL-3.0-or-later
// Reseam-derived material: see NOTICE for applicable section 7 terms.

package top.madkarma.patches.googlephotos.gms

import app.reseam.patch.*

private val homeActivity = klass("com.google.android.apps.photos.home.HomeActivity")

internal val homeActivityOnStart = homeActivity.method("onStart") { params() }

internal val accountPermissionResult =
    homeActivity.method("onRequestPermissionsResult", inherited = true) {
        params(Type.Int, "[Ljava/lang/String;", "[I")
    }

internal val playServicesCheck = method("playServicesCheck") {
    strings("Google Play Services not available")
    params(Type.Context, Type.Int)
    returns(Type.Void)
}

internal val playServicesAvailability = method("playServicesAvailability") {
    strings("com.google.android.gms.version")
    params(Type.Context, Type.Int)
    returns(Type.Int)
}

internal val googleAccounts = method("googleAccounts") {
    strings("com.google.android.gms.auth.accounts")
    params(Type.Context)
    returns("[Landroid/accounts/Account;")
}

private val accountAccessResponse = klass("accountAccessResponse") {
    strings("requestGoogleAccountsAccess")
}.method("<init>")

internal val accountAccessTask = method("accountAccessTask") {
    calls(accountAccessResponse)
    name("call")
}

internal val accountAccessTaskConstructor = method("accountAccessTaskConstructor") {
    inClass(klass(accountAccessTask.owner))
    name("<init>")
}

internal val accountAccessClientConstructor = method("accountAccessClientConstructor") {
    inClass(klass(accountAccessTaskConstructor.parameterTypes.single()))
    name("<init>")
    params(Type.Context, "java.util.concurrent.ExecutorService")
}

private val broadcastAccountsListener = klass("broadcastAccountsListener") {
    strings("android.accounts.LOGIN_ACCOUNTS_CHANGED")
    hasInstanceField("android.accounts.OnAccountsUpdateListener")
}.method("<init>") { hasParam("android.accounts.OnAccountsUpdateListener") }

// The factory shares the listener between the framework and broadcast account-update paths.
internal val registerAccountsListener = method("registerAccountsListener") {
    calls(broadcastAccountsListener)
}

// The scheduler has no diagnostic strings; the retained task constructor identifies its caller.
internal val checkAccountValidity = method("checkAccountValidity") {
    calls {
        owner($$"com.google.android.apps.photos.login.AccountValidityMonitor$CheckAccountTask")
        name("<init>")
    }
    returns(Type.Void)
}

private val frictionlessLogin = klass("frictionlessLogin") {
    strings("checkPlayServices", "ProvideFrctAccountTask")
}

internal val frictionlessEligibility = method("frictionlessEligibility") {
    inClass(frictionlessLogin)
    returns(Type.Boolean)
    params()
    calls { params(Type.Int); returns(Type.Void) }
}

internal val frictionlessServicesAvailable = method("frictionlessServicesAvailable") {
    calledBy(frictionlessEligibility)
    params()
    returns(Type.Boolean)
}

private val accountHandler = klass("accountHandler") { strings("AccountChangeHandler") }

internal val loginServices = fieldTarget("loginServices") {
    frictionlessLogin.fieldOfType(frictionlessServicesAvailable.owner).ref
}

internal val loginAccountHandler = fieldTarget("loginAccountHandler") {
    frictionlessLogin.fieldOfType(accountHandler.descriptor).ref
}

private val startFrictionlessLogin = method("startFrictionlessLogin") {
    inClass(frictionlessLogin)
    strings("checkPlayServices", "ProvideFrctAccountTask")
}

// The same login flow already restores the saved selection through this no-argument call.
internal val restoreSelectedAccount = method("restoreSelectedAccount") {
    inClass(accountHandler)
    calledBy(startFrictionlessLogin)
    params()
    returns(Type.Void)
}

private val notificationRegistration = method("notificationRegistration") {
    strings("Exception reading GServices key.")
}

internal val notificationRegistrationConstructor = method("notificationRegistrationConstructor") {
    inClass(klass(notificationRegistration.owner))
    name("<init>")
    hasParam(Type.Context)
}
