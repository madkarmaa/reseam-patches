// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.shared.gmscore

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.reseam.runtime.Export

private const val RESEAM_VENDOR = "app.reseam"
private const val RESEAM_DOWNLOAD = "https://reseam.app/download/#gmscore"

@Volatile
private var promptOpen = false

internal fun openMissingPrompt(context: Context) {
    if (promptOpen) return
    promptOpen = true

    try {
        context.startActivity(
            Intent(
                context, GmsCoreMissingActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (exception: RuntimeException) {
        promptOpen = false
        Log.w(TAG, "Cannot ask for GmsCore", exception)
    }
}

/** An activity rather than a dialog because a failed sign-in can finish the activity that started it. */
@Export
class GmsCoreMissingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val night =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) android.R.style.Theme_DeviceDefault_Dialog_Alert
        else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

        val appName = applicationInfo.loadLabel(packageManager)

        val dialog = AlertDialog.Builder(this, theme).setTitle("GmsCore is not installed")
            .setMessage("$appName needs GmsCore (${gmsCorePackageName()}) for Google sign-in. Install it and add your Google account there.")
            .setNegativeButton(android.R.string.cancel, null).setOnDismissListener { finish() }

        // Reseam publishes only its own GmsCore; other vendor groups come from their own publishers.
        if (vendorGroupId() == RESEAM_VENDOR) dialog.setPositiveButton("Get GmsCore") { _, _ -> openDownloadPage() }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        promptOpen = false
    }

    private fun openDownloadPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RESEAM_DOWNLOAD)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open $RESEAM_DOWNLOAD", Toast.LENGTH_LONG).show()
        }
    }
}
