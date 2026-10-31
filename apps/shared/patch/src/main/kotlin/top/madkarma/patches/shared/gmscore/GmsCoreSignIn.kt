// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.shared.gmscore

import app.reseam.patch.*
import top.madkarma.shared.gmscore.bindings.GmsCore

private const val PLAY_SERVICES = "com.google.android.gms"
private const val MISSING_ACTIVITY = "top.madkarma.shared.gmscore.GmsCoreMissingActivity"
private const val ENTRY_APPLICATION = "top.madkarma.shared.gmscore.GmsCoreApplication"

// Google Sign-In's hub activity opens the Play Services sign-in screen here.
private val signInLauncher = method("signInLauncher") {
    inClass(klass("com.google.android.gms.auth.api.signin.internal.SignInHubActivity"))
    strings("com.google.android.gms.auth.GOOGLE_SIGN_IN", PLAY_SERVICES)
}

private val signInLaunch = signInLauncher.point {
    invokeVirtual { name("startActivityForResult"); params("android.content.Intent", Type.Int) }
}.captureArgumentAs("intent", 1, "android.content.Intent")

// Credential Manager's Google ID options store their request in a Bundle, built by these methods.
private val googleIdRequests = listOf(
    "com.google.android.libraries.identity.googleid.BUNDLE_KEY_FILTER_BY_AUTHORIZED_ACCOUNTS",
    "com.google.android.libraries.identity.googleid.siwg.BUNDLE_KEY_SERVER_CLIENT_ID",
).map { key -> methods("googleIdRequest") { strings(key); returns("android.os.Bundle") } }

/**
 * Opens Google Sign-In through the GmsCore of [vendor], and asks for GmsCore when a sign-in starts
 * without it. Service bindings call `GmsCore.servicePackageOverride` and `GmsCore.serviceAction`.
 */
fun PatchRuntime.signInThroughGmsCore(vendor: String) {
    GmsCore.vendorGroupId.implement { returnValue(string(vendor)) }

    // The app entry point needs an Application class inside the app, which the framework's own is not.
    manifest.edit {
        val application = findByTag("application").single()

        if (application["android:name"] in setOf(null, "android.app.Application")) {
            application["android:name"] = ENTRY_APPLICATION
            log.info("GmsCore: $ENTRY_APPLICATION hosts the app entry point.")
        }
    }
    appEntry { GmsCore.start(application) }

    // The prompt joins the app's task, so sign-in screens the app opens next stay above it.
    manifest.addActivity(MISSING_ACTIVITY) {
        this["android:theme"] = "@android:style/Theme.Translucent.NoTitleBar"
    }

    signInLaunch.before { GmsCore.prepareSignIn(thisObject, capture("intent")) }
    check(signInLauncher.method.replaceAllStrings(PLAY_SERVICES, "$vendor.android.gms") > 0)
    log.info("GmsCore: ${signInLauncher.descriptor} opens the GmsCore sign-in screen after checking that GmsCore is installed.")

    for (request in googleIdRequests.flatMap { it.all }) {
        request.after { GmsCore.checkGoogleIdRequest(result) }
        log.info("GmsCore: ${request.descriptor} checks that GmsCore is installed for an interactive sign-in.")
    }
}
