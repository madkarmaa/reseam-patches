// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: AGPL-3.0-or-later
// Reseam-derived material: see NOTICE for applicable section 7 terms.

@file:JvmName("GmsCoreSupport") @file:Suppress("unused")

package top.madkarma.googlephotos.gms

import android.Manifest
import android.accounts.Account
import android.accounts.OnAccountsUpdateListener
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.RemoteException
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import app.reseam.runtime.Export
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "ReseamPhotos"
private const val RESEAM_VENDOR = "app.reseam"
private const val ACCOUNT_PERMISSION_REQUEST = 0x5250
private const val PREFERENCES = "reseam_photos_gms"
private const val MEDIA_PERMISSION_HANDLED = "media_permission_handled"
private val accountRefreshExecutor = Executors.newSingleThreadExecutor()
private val accountsListeners = CopyOnWriteArrayList<WeakReference<OnAccountsUpdateListener>>()

@Volatile
private var accountAccessRequest: Callable<*>? = null

@Volatile
private var accountAccessRejected = false

@Volatile
private var foregroundActivity = WeakReference<Activity>(null)
private var permissionRequestActivity = WeakReference<Activity>(null)
private var activePrompt = WeakReference<AlertDialog>(null)

@Export
fun initialize(request: Callable<*>?) {
    accountAccessRequest = request
}

@Export
fun getAccountRefreshExecutor(): ExecutorService = accountRefreshExecutor

@Export
fun withPackageName(context: Context, packageName: String): Context =
    object : ContextWrapper(context) {
        override fun getPackageName(): String = packageName
    }

@Suppress("UseKtx")
@Export
fun check(activity: Activity, packageName: String, vendor: String) {
    foregroundActivity = WeakReference(activity)
    try {
        if (!isInstalled(activity, packageName)) {
            Log.w(TAG, "GmsCore is not installed: $packageName")
            prompt(
                activity,
                "GmsCore is not installed",
                "Google Photos needs GmsCore ($packageName) to sign in. Install it, open it once and grant its permissions.",
                // Reseam publishes only its own GmsCore; other vendor groups come from their own publishers.
                "Get GmsCore".takeIf { vendor == RESEAM_VENDOR },
            ) {
                open(
                    activity,
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://reseam.app/download/#gmscore"),
                    ),
                )
            }
            return
        }

        if (accountAccessRejected && !hasExtendedAccess(activity, vendor)) {
            if (!isMediaPermissionHandled(activity)) return
            Log.i(TAG, "GmsCore account access required")
            requestAccountPermission(activity, vendor)
            return
        }

        val power = activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val automotive = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

        if (!automotive && power != null && !power.isIgnoringBatteryOptimizations(packageName)) {
            Log.w(TAG, "GmsCore is battery optimized: $packageName")
            prompt(
                activity,
                "GmsCore is battery optimized",
                "Turn battery optimization off for GmsCore so Google Photos can stay signed in and back up in the background.",
                "Open settings",
            ) {
                var intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                if (intent.resolveActivity(activity.packageManager) == null) intent =
                    Intent(Settings.ACTION_SETTINGS)
                open(activity, intent)
            }
            return
        }

        val provider = Uri.parse("content://$vendor.android.gsf.gservices/prefix")
        activity.contentResolver.acquireContentProviderClient(provider).use { client ->
            if (client != null) {
                Log.i(TAG, "GmsCore is available: $packageName")
                return
            }
        }

        Log.w(TAG, "GmsCore provider is unavailable: $provider")
        prompt(
            activity,
            "GmsCore is unavailable",
            "Open GmsCore and check its permissions and background activity settings.",
            "Open GmsCore",
        ) {
            val intent = activity.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) open(activity, intent)
        }
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Cannot check GmsCore availability", exception)
    }
}

private fun hasExtendedAccess(context: Context, vendor: String): Boolean =
    context.checkSelfPermission("$vendor.org.microg.gms.EXTENDED_ACCESS") == PackageManager.PERMISSION_GRANTED

@Export
@Throws(RemoteException::class)
fun readAccounts(context: Context, vendor: String): Array<Account> {
    try {
        val accounts = queryAccounts(context, vendor)
        accountAccessRejected = false
        return accounts
    } catch (exception: RemoteException) {
        if (!isAccountPermissionRejection(exception)) throw exception

        accountAccessRejected = true
        Log.i(TAG, "GmsCore rejected account access; extended access is required")

        val activity = foregroundActivity.get()
        activity?.runOnUiThread { check(activity, "$vendor.android.gms", vendor) }

        return emptyArray()
    }
}

private fun isAccountPermissionRejection(exception: Exception): Boolean {
    var cause: Throwable? = exception
    while (cause != null) {
        val message = cause.message

        if (message != null && (message.contains("missing google package permission for ACCOUNT") || message.contains(
                "missing google package permission or GET_ACCOUNTS"
            ) || message.contains("missing EXTENDED_ACCESS permission"))
        ) return true

        cause = cause.cause
    }

    return false
}

@Suppress("DEPRECATION")
private fun queryAccounts(context: Context, vendor: String): Array<Account> {
    val authority = "$vendor.android.gms.auth.accounts"
    try {
        context.contentResolver.acquireUnstableContentProviderClient(authority).use { client ->
            if (client == null) throw RemoteException("The $authority provider is not available.")

            val extras = Bundle()
            extras.putString(
                "callingActivity",
                if (context is Activity) context.componentName.className else "",
            )

            val response = client.call("get_accounts", vendor, extras)
                ?: throw RemoteException("Null result from the accounts provider")

            val accounts = if (Build.VERSION.SDK_INT >= 33) response.getParcelableArray(
                "accounts",
                Account::class.java,
            )
            else response.getParcelableArray("accounts")

            if (accounts == null) throw RemoteException("The accounts provider returned no accounts array")

            return Arrays.copyOf(accounts, accounts.size, Array<Account>::class.java)
        }
    } catch (exception: RuntimeException) {
        // Keep Photos' account-query exception contract, including permission errors.
        throw RemoteException("Accounts ContentProvider failed: ${exception.message}")
    }
}

@Export
fun rememberAccountsListener(listener: OnAccountsUpdateListener?) {
    for (reference in accountsListeners) {
        val existing = reference.get()

        if (existing === listener) return
        if (existing == null) accountsListeners.remove(reference)
    }

    accountsListeners.add(WeakReference(listener))
}

private fun refreshAccounts(activity: Activity, vendor: String) {
    accountRefreshExecutor.execute {
        try {
            val context = activity.applicationContext
            checkNotNull(accountAccessRequest) { "Account access request is not initialized" }.call()

            val accounts = readAccounts(context, vendor)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                for (reference in accountsListeners) {
                    val listener = reference.get()

                    if (listener == null) {
                        accountsListeners.remove(reference)
                        continue
                    }

                    listener.onAccountsUpdated(accounts)
                }

                Log.i(
                    TAG,
                    "Refreshed GmsCore accounts after permission grant: ${accounts.size}",
                )

                // Recreating alone restores Photos' earlier signed-out onboarding state.
                val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)

                if (launch == null) {
                    activity.recreate()
                    return@runOnUiThread
                }

                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity.startActivity(launch)
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Cannot refresh GmsCore accounts", exception)
        }
    }
}

private fun requestAccountPermission(activity: Activity, vendor: String) {
    if (activity.isFinishing || activity.isDestroyed || permissionRequestActivity.get() === activity) return

    Log.i(TAG, "Requesting GmsCore account access")

    permissionRequestActivity = WeakReference(activity)
    activity.requestPermissions(
        arrayOf("$vendor.org.microg.gms.EXTENDED_ACCESS"),
        ACCOUNT_PERMISSION_REQUEST,
    )
}

@Suppress("UseKtx")
@Export
fun onAccountPermissionResult(
    activity: Activity,
    requestCode: Int,
    permissions: Array<String?>,
    results: IntArray,
    vendor: String,
): Boolean {
    if (requestCode != ACCOUNT_PERMISSION_REQUEST || permissions.size != 1 || "$vendor.org.microg.gms.EXTENDED_ACCESS" != permissions[0]) {
        for (permission in permissions) {
            if (!isMediaPermission(permission)) continue

            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putBoolean(MEDIA_PERMISSION_HANDLED, true).apply()

            activity.window.decorView.post { check(activity, "$vendor.android.gms", vendor) }
            break
        }

        return false
    }

    permissionRequestActivity = WeakReference(activity)
    if (results.size != 1 || results[0] != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "GmsCore account access denied")
        return true
    }

    Log.i(TAG, "GmsCore account access granted")
    refreshAccounts(activity, vendor)
    return true
}

private fun isMediaPermissionHandled(context: Context): Boolean {
    if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(MEDIA_PERMISSION_HANDLED, false)
    ) return true

    if (Build.VERSION.SDK_INT < 33) return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(
        Manifest.permission.READ_MEDIA_VIDEO
    ) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
}

private fun isMediaPermission(permission: String?): Boolean =
    permission == Manifest.permission.READ_EXTERNAL_STORAGE || permission == Manifest.permission.READ_MEDIA_IMAGES || permission == Manifest.permission.READ_MEDIA_VIDEO || permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED

private fun isInstalled(context: Context, packageName: String): Boolean {
    try {
        context.packageManager.getPackageInfo(packageName, 0)
        return true
    } catch (exception: PackageManager.NameNotFoundException) {
        return false
    }
}

private fun prompt(
    activity: Activity,
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    if (activity.isFinishing || activity.isDestroyed) return

    val previous = activePrompt.get()
    if (previous != null && previous.isShowing) return

    val builder =
        AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false)
            .setNegativeButton("Ignore") { prompt, _ -> prompt.dismiss() }
    if (action != null) builder.setPositiveButton(action) { _, _ -> onAction() }
    val dialog = builder.create()

    activePrompt = WeakReference(dialog)
    dialog.show()
}

private fun open(activity: Activity, intent: Intent) {
    try {
        activity.startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(activity, "No app is available to open this screen", Toast.LENGTH_LONG)
            .show()
    }
}
