// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("MindfulLaunch")
@file:Suppress("unused")

package top.madkarma.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import app.reseam.runtime.Export
import java.lang.ref.WeakReference
import java.util.Calendar
import top.madkarma.niagara.settings.MindfulLaunchSettings
import top.madkarma.niagara.settings.NiagaraStrings

private const val TAG = "NiagaraMindfulLaunch"
private const val PREFS = "bitpit.launcher_preferences"
private const val KEY_USAGE_BREAKER = "bitpit.launcher.key.ENABLE_USAGE_BREAKER"
private const val MINUTE_MS = 60_000L

// Niagara's leisure-app feed: the apps the user picked for Usage Breaker.
private var leisureApps: Collection<*>? = null

// The item and view of the launch in progress, read when Niagara starts the app.
private var tappedItem = WeakReference<Any>(null)
private var tappedView = WeakReference<View>(null)

@Export
fun watchLeisureApps(feed: Any?) {
    leisureApps = feed as? Collection<*>
}

@Export
fun rememberTap(item: Any?, view: View?) {
    tappedItem = WeakReference(item)
    tappedView = WeakReference(view)
}

/**
 * Starts the app Niagara launches, or first shows the pause sheet when it is an overused leisure
 * app. The sheet's open button starts the same intent.
 */
@Export
fun startActivity(context: Context?, intent: Intent?, options: Bundle?) {
    checkNotNull(context) { "Niagara launched an app without a context" }
    checkNotNull(intent) { "Niagara launched an app without an intent" }

    val item = tappedItem.get()
    val view = tappedView.get()
    rememberTap(null, null)

    val pause = view?.let { pauseFor(it, item, intent) }
    if (pause == null) {
        context.startActivity(intent, options)
        return
    }

    showPauseSheet(pause.activity, pause.themed, pause.content) {
        startPaused(context, intent, options)
    }
    Log.i(TAG, "Paused launching ${intent.component?.packageName}")
}

private class Pause(val activity: Activity, val themed: Context, val content: PauseContent)

private fun pauseFor(view: View, item: Any?, intent: Intent): Pause? {
    val themed = view.context
    val activity = themed.activity() ?: return null
    val packageName = intent.component?.packageName ?: return null

    if (!MindfulLaunchSettings.mindfulLaunch.value) return null
    if (!usageBreakerEnabled(themed)) return null
    if (leisureApps?.contains(item) != true) return null

    val appName = appLabel(themed, intent) ?: packageName
    val message = pauseMessage(themed, packageName, appName) ?: return null

    val content =
        PauseContent(
            title = NiagaraStrings.pauseTitle,
            message = message,
            openLabel = NiagaraStrings.pauseOpen(appName),
            countdownLabel = { seconds -> NiagaraStrings.pauseOpenCountdown(seconds) },
            dismissLabel = NiagaraStrings.pauseDismiss,
            countdownSeconds = MindfulLaunchSettings.countdown.value.toIntOrNull() ?: 0,
        )
    return Pause(activity, themed, content)
}

// The app may be uninstalled or disabled while the sheet is shown.
private fun startPaused(context: Context, intent: Intent, options: Bundle?) {
    try {
        context.startActivity(intent, options)
    } catch (exception: ActivityNotFoundException) {
        Log.w(TAG, "Paused app is no longer available", exception)
    }
}

private fun appLabel(context: Context, intent: Intent): String? =
    intent
        .resolveActivityInfo(context.packageManager, 0)
        ?.loadLabel(context.packageManager)
        ?.toString()

/** The reasons to pause, or null when [packageName] is within every limit. */
private fun pauseMessage(context: Context, packageName: String, appName: String): String? {
    val openLimit = MindfulLaunchSettings.openLimit.value.toIntOrNull() ?: 0
    val windowMinutes = MindfulLaunchSettings.openWindow.value.toLongOrNull() ?: 60
    val dailyMinutes = MindfulLaunchSettings.dailyLimit.value.toLongOrNull() ?: 0
    if (openLimit <= 0 && dailyMinutes <= 0) return null

    val now = System.currentTimeMillis()
    val usage =
        measureUsage(
            context,
            packageName,
            windowStart = now - windowMinutes * MINUTE_MS,
            dayStart = startOfToday(now),
            now = now,
        )

    val reasons = mutableListOf<String>()
    if (openLimit > 0 && usage.opens >= openLimit)
        reasons += NiagaraStrings.pauseOpens(appName, usage.opens, windowLabel(windowMinutes))
    if (dailyMinutes > 0 && usage.foregroundToday >= dailyMinutes * MINUTE_MS)
        reasons += NiagaraStrings.pauseUsage(appName, durationLabel(usage.foregroundToday))
    if (reasons.isEmpty()) return null

    reasons += NiagaraStrings.pausePrompt
    return reasons.joinToString("\n\n")
}

private fun usageBreakerEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USAGE_BREAKER, false)

private fun startOfToday(now: Long): Long =
    Calendar.getInstance()
        .apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        .timeInMillis

private fun windowLabel(minutes: Long): String =
    when (minutes) {
        15L -> NiagaraStrings.duration15Minutes
        30L -> NiagaraStrings.duration30Minutes
        60L -> NiagaraStrings.duration1Hour
        120L -> NiagaraStrings.duration2Hours
        240L -> NiagaraStrings.duration4Hours
        else -> durationLabel(minutes * MINUTE_MS)
    }

private fun durationLabel(millis: Long): String {
    val minutes = millis / MINUTE_MS
    if (minutes < 60) return NiagaraStrings.usageMinutes(minutes.toInt())
    return NiagaraStrings.usageHoursMinutes((minutes / 60).toInt(), (minutes % 60).toInt())
}

private fun Context.activity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
