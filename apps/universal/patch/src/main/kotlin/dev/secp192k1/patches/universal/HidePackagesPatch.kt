// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package dev.secp192k1.patches.universal

import app.reseam.patch.before
import app.reseam.patch.patch
import app.reseam.patch.redirectTo
import dev.secp192k1.universal.hidepackages.bindings.HiddenPackages

private val packageNamePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

val hidePackages = patch("Hide packages") {
    description("Hides the chosen packages from the app's PackageManager queries, as if they were not installed.")

    val packageNamesOption = stringListOption(
        "packageNames",
        title = "Packages to hide",
        description = "Package names the app must not see.",
        required = true,
    )

    // Include calls that other patches added to their extensions.
    afterDependents {
        val packageNames =
            options[packageNamesOption].map(String::trim).filter(String::isNotEmpty).distinct()
        require(packageNames.isNotEmpty()) { "Name at least one package to hide" }

        val invalidPackageNames = packageNames.filterNot(packageNamePattern::matches)
        require(invalidPackageNames.isEmpty()) { "Not package names: ${invalidPackageNames.joinToString()}" }

        val hiddenPackageNames = packageNames.joinToString(",")

        var redirectedCallSites = 0
        for (wrapper in packageManagerWrappers) {
            val callSites = packageManagerCallSites(wrapper)

            for (callSite in callSites) {
                // Initialize on every call path, including queries before Application.onCreate().
                callSite.before { call(HiddenPackages.initialize, string(hiddenPackageNames)) }
                callSite.redirectTo(wrapper)
            }

            redirectedCallSites += callSites.size
        }

        if (redirectedCallSites == 0) error("The app makes none of the PackageManager calls this patch wraps")

        log.info("Hid ${packageNames.size} packages at $redirectedCallSites PackageManager call sites")
    }
}
