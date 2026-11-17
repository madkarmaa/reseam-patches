// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.turuta.premium

import app.reseam.patch.FileScope
import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

internal val attachPreferences =
    method("attachPreferences") {
        strings("FlutterSharedPreferences")
        calls { name("getSharedPreferences") }
    }

internal val convertPurchases =
    method("convertPurchases") {
        strings("purchaseState", "obfuscatedProfileId")
    }

// R8 renames Play Billing's Purchase in newer builds; its toString diagnostic still names it.
private val purchase = klass("purchase") { strings("Purchase. Json: ") }

// Parses the receipt JSON and its signature.
internal val purchaseConstructor = purchase.method("<init>") { params(Type.String, Type.String) }

// Flutter stores the preference keys in its native Dart snapshot, outside the DEX queries. Split
// installs ship the snapshot in an ABI split rather than the base APK.
internal fun requirePremiumPreferences(files: FileScope) {
    val snapshots =
        files.components().flatMap { component ->
            val componentFiles = files.component(component)
            componentFiles
                .list()
                .filter { it.startsWith("lib/") && it.endsWith("/libapp.so") }
                .map { componentFiles to it }
        }

    check(snapshots.isNotEmpty()) { "TuRuta Dart snapshot not found" }

    for ((componentFiles, path) in snapshots) {
        val snapshot =
            componentFiles.read(path)?.toString(Charsets.ISO_8859_1)
                ?: error("Could not read TuRuta Dart snapshot: $path")

        for (key in
            listOf(
                "has_active_premium_cache",
                "PK_show_ads",
                "lifetime_pass_a",
            )) check(key in snapshot) { "TuRuta premium preference '$key' not found in $path" }
    }
}
