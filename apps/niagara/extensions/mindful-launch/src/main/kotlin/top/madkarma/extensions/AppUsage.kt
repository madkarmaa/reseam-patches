// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.extensions

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** How often an app was opened within a window, and how long it was in the foreground today. */
internal class AppUsage(val opens: Int, val foregroundToday: Long)

// Leaving an app for less than this, such as for a share sheet or a permission prompt, keeps the
// same visit.
private const val REOPEN_GAP_MS = 60_000L

// The platform's ACTIVITY_RESUMED and ACTIVITY_PAUSED, which older releases name
// MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND.
private const val ACTIVITY_RESUMED = 1
private const val ACTIVITY_PAUSED = 2

/**
 * Reads Android's usage events for [packageName]. Without usage access the platform returns no
 * events, so the app counts as unused.
 */
internal fun measureUsage(
    context: Context,
    packageName: String,
    windowStart: Long,
    dayStart: Long,
    now: Long,
): AppUsage {
    val manager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return AppUsage(0, 0)

    val start = minOf(windowStart, dayStart)
    val events = manager.queryEvents(start, now) ?: return AppUsage(0, 0)

    val resumedActivities = HashSet<String>()
    var visibleSince = -1L
    var lastLeftAt = Long.MIN_VALUE / 2
    var opens = 0
    var foregroundToday = 0L
    var firstEvent = true
    val event = UsageEvents.Event()

    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.packageName != packageName) continue

        val time = event.timeStamp
        val activity = event.className ?: ""

        when (event.eventType) {
            ACTIVITY_RESUMED -> {
                if (resumedActivities.isEmpty()) {
                    if (time >= windowStart && time - lastLeftAt >= REOPEN_GAP_MS) opens++
                    visibleSince = time
                }
                resumedActivities.add(activity)
                firstEvent = false
            }

            ACTIVITY_PAUSED -> {
                // A pause without its resume means the app was visible when the range started.
                val wasVisible = resumedActivities.remove(activity) || firstEvent
                firstEvent = false
                if (!wasVisible || resumedActivities.isNotEmpty()) continue

                val since = if (visibleSince >= 0) visibleSince else start
                foregroundToday += overlap(since, time, dayStart)
                visibleSince = -1
                lastLeftAt = time
            }
        }
    }

    if (visibleSince >= 0) foregroundToday += overlap(visibleSince, now, dayStart)
    return AppUsage(opens, foregroundToday)
}

private fun overlap(from: Long, to: Long, dayStart: Long): Long =
    maxOf(0L, to - maxOf(from, dayStart))
