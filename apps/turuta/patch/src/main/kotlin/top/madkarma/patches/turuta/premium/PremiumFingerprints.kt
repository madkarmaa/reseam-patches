// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.turuta.premium

import app.reseam.patch.FileScope
import app.reseam.patch.method

internal val attachPreferences = method("attachPreferences") {
    strings("FlutterSharedPreferences")
    calls { name("getSharedPreferences") }
}

internal val convertPurchases = method("convertPurchases") {
    strings("purchaseState", "obfuscatedProfileId")
}

// Flutter stores the preference keys in its native Dart snapshot, outside the DEX queries.
internal fun requirePremiumPreferences(files: FileScope) {
    val snapshots = files.list().filter { it.startsWith("lib/") && it.endsWith("/libapp.so") }
    check(snapshots.isNotEmpty()) { "TuRuta Dart snapshot not found" }

    for (path in snapshots) {
        val snapshot = files.read(path)?.toString(Charsets.ISO_8859_1)
            ?: error("Could not read TuRuta Dart snapshot: $path")

        for (key in listOf(
            "has_active_premium_cache", "PK_show_ads", "lifetime_pass_a"
        )) check(key in snapshot) { "TuRuta premium preference '$key' not found in $path" }
    }
}
