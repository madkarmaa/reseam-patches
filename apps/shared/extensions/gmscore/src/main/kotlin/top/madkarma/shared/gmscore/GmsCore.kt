// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("GmsCore") @file:Suppress("unused")

package top.madkarma.shared.gmscore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import app.reseam.runtime.Export

internal const val TAG = "GmsCore"
private const val PLAY_SERVICES_NAMESPACE = "com.google.android.gms"
private const val SIGN_IN_SERVICE = "$PLAY_SERVICES_NAMESPACE.signin.service.START"
private const val CREDENTIALS_SERVICE = "$PLAY_SERVICES_NAMESPACE.identitycredentials.service.START"
private const val GOOGLE_ID = "com.google.android.libraries.identity.googleid"
private const val FILTER_BY_AUTHORIZED_ACCOUNTS =
    "$GOOGLE_ID.BUNDLE_KEY_FILTER_BY_AUTHORIZED_ACCOUNTS"
private const val TOKEN_SUBTYPE = "$GOOGLE_ID.BUNDLE_KEY_GOOGLE_ID_TOKEN_SUBTYPE"
private const val SIGN_IN_WITH_GOOGLE = "$GOOGLE_ID.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL"

/** The GmsCore vendor group selected at patch time. */
@Export
external fun vendorGroupId(): String

internal fun gmsCorePackageName() = "${vendorGroupId()}.android.gms"

private var application: Context? = null

@Export
fun start(context: Context?) {
    application = context?.applicationContext
}

// Credential Manager requests Google ID tokens through the identity credentials service.
private fun isAuthentication(action: String): Boolean =
    action.startsWith("$PLAY_SERVICES_NAMESPACE.auth.") || action == SIGN_IN_SERVICE || action == CREDENTIALS_SERVICE

/** The GmsCore package for authentication services, or null to leave the service on Play Services. */
@Export
fun servicePackageOverride(action: String?): String? {
    if (action == null || !isAuthentication(action)) return null

    Log.i(TAG, "Routing $action through ${gmsCorePackageName()}")
    return gmsCorePackageName()
}

/** The service action as GmsCore registers it. */
@Export
fun serviceAction(action: String): String {
    val context = application ?: return action
    return registeredAction(action) { context.packageManager.queryIntentServices(it, 0) }
}

/** Points the GmsCore sign-in screen intent at its registered action, after asking for GmsCore when it is missing. */
@Export
fun prepareSignIn(activity: Activity, intent: Intent) {
    if (intent.`package` != gmsCorePackageName()) return
    val action = intent.action ?: return

    requireInstalled(activity)
    intent.action =
        registeredAction(action) { activity.packageManager.queryIntentActivities(it, 0) }
}

/**
 * Asks for GmsCore when a Credential Manager request shows Google's account picker: a Sign in with
 * Google button, or a Google ID request open to every account. Requests limited to authorized
 * accounts sign in silently, often at launch.
 */
@Export
fun checkGoogleIdRequest(request: Bundle?) {
    val context = application ?: return
    if (request == null) return

    val signInButton = request.getString(TOKEN_SUBTYPE) == SIGN_IN_WITH_GOOGLE
    val accountPicker = request.containsKey(FILTER_BY_AUTHORIZED_ACCOUNTS) && !request.getBoolean(
        FILTER_BY_AUTHORIZED_ACCOUNTS
    )
    if (signInButton || accountPicker) requireInstalled(context)
}

// Reseam and ReVanced GmsCore register Play Services actions, while MicroG-RE registers them under its vendor group.
private fun registeredAction(action: String, resolve: (Intent) -> List<*>): String {
    if (!action.startsWith("$PLAY_SERVICES_NAMESPACE.")) return action

    val gmsCore = gmsCorePackageName()
    if (resolve(Intent(action).setPackage(gmsCore)).isNotEmpty()) return action

    val vendorAction = vendorGroupId() + action.removePrefix("com.google")
    if (resolve(Intent(vendorAction).setPackage(gmsCore)).isEmpty()) return action

    Log.i(TAG, "GmsCore registers $action as $vendorAction")
    return vendorAction
}

// Only sign-in requests ask for GmsCore, since apps also bind its services at launch.
private fun requireInstalled(context: Context) {
    val gmsCore = gmsCorePackageName()

    try {
        context.packageManager.getPackageInfo(gmsCore, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        Log.w(TAG, "GmsCore is not installed: $gmsCore")
        openMissingPrompt(context)
    }
}
