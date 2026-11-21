// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.swiftkey.themes

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import app.reseam.runtime.Export
import java.io.IOException

private const val TAG = "SwiftKeyThemePacks"
private const val PICK_THEME_PACK = 1

// The manifest names the app's themes screen, which opens once a pack is installed.
private const val THEMES_SCREEN = "top.madkarma.swiftkey.themes.THEMES_SCREEN"

// The themes screen opens on the tab that lists custom themes, the installed packs among them.
private const val THEMES_SCREEN_TAB = "themes_activity_launch_tab"
private const val CUSTOM_THEMES_TAB = 2

/** Installs the theme pack it is opened or shared with, or asks for one. */
@Export
class InstallThemeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pack = intent.data ?: sharedStream()
        if (pack != null) {
            install(pack)
            return
        }

        if (savedInstanceState != null) return

        val pick =
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")

        try {
            startActivityForResult(pick, PICK_THEME_PACK)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app can pick a theme file", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Deprecated("Activity result API of the platform")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val pack = data?.data

        if (requestCode != PICK_THEME_PACK || resultCode != RESULT_OK || pack == null) {
            finish()
            return
        }

        install(pack)
    }

    private fun sharedStream(): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)

        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(Intent.EXTRA_STREAM)
    }

    private fun install(pack: Uri) {
        val context = applicationContext

        Thread {
            var installed = false

            val message =
                try {
                    val names = installThemePack(context, pack)
                    installed = true
                    "Installed ${names.joinToString()}"
                } catch (exception: IOException) {
                    Log.w(TAG, "Cannot install the theme pack $pack", exception)
                    exception.message ?: "Cannot install the theme pack"
                } catch (exception: SecurityException) {
                    Log.w(TAG, "Cannot read the theme pack $pack", exception)
                    "Cannot read the theme pack"
                }

            runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                if (installed) openThemesScreen()
                finish()
            }
        }
            .start()
    }

    private fun openThemesScreen() {
        val screen =
            packageManager
                .getActivityInfo(ComponentName(this, javaClass), PackageManager.GET_META_DATA)
                .metaData
                ?.getString(THEMES_SCREEN) ?: return

        startActivity(
            Intent().setClassName(this, screen).putExtra(THEMES_SCREEN_TAB, CUSTOM_THEMES_TAB),
        )
    }
}
