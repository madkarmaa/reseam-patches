// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.mindfullaunch

import app.reseam.patch.*

// An app item's launch is the only caller of LauncherApps.startMainActivity.
internal val launchApp =
    method("launchApp") {
        calls {
            owner("android.content.pm.LauncherApps")
            name("startMainActivity")
        }
    }

// Apps of the primary profile, the only ones Usage Breaker tracks, start through their intent.
internal val startPrimaryApp =
    launchApp.point("startPrimaryApp") {
        invokeVirtual {
            owner("android.content.Context")
            name("startActivity")
        }
    }

private val appFeed = klass("appFeed") { strings("Leisure") }

private val usageBreaker = klass("usageBreaker") { strings("isUsageBreakerEnabled") }

private val leisureUseInfo = klass("leisureUseInfo") { strings("LeisureUseInfo(totalLeisureUse=") }

// Usage Breaker sums its leisure use over the apps in the leisure feed.
private val measureLeisureUse =
    method("measureLeisureUse") {
        inClass(usageBreaker)
        calls {
            owner(leisureUseInfo.descriptor)
            name("<init>")
        }
    }

internal val leisureApps = measureLeisureUse.field("leisureApps") { type(appFeed.descriptor) }

internal val feedsConstructor =
    method("feedsConstructor") {
        inClass(klass(leisureApps.owner))
        name("<init>")
    }
