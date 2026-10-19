// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.turuta.gms

import app.reseam.patch.*
import top.madkarma.turuta.gms.bindings.GmsCoreSupport

val gmsCoreSupport = patch("GmsCore support") {
    description("Signs in through separately installed Reseam GmsCore.")
    compatibleWith("pe.tumicro.android")

    val vendorGroupId = stringOption(
        "gmsCoreVendorGroupId",
        title = "GmsCore vendor group ID",
        default = "app.reseam",
        required = true,
    )

    execute {
        val vendor = options[vendorGroupId]
        require(
            Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+").matches(vendor) && !vendor.endsWith(
                ".android.gms"
            )
        ) {
            "gmsCoreVendorGroupId must be a vendor group such as app.reseam"
        }
        val gmsPackage = "$vendor.android.gms"

        check(signInActivityCreate.replaceAllStrings("com.google.android.gms", gmsPackage) > 0)
        log.info("GmsCore: ${signInActivityCreate.descriptor} opens the GmsCore sign-in activity.")

        check(tokenServiceInitializer.replaceAllStrings("com.google.android.gms", gmsPackage) > 0)
        log.info("GmsCore: ${tokenServiceInitializer.descriptor} binds to the GmsCore GetToken service.")

        modernTokenApiAvailable.alwaysReturn(false)
        log.info("GmsCore: ${modernTokenApiAvailable.descriptor} selects the supported GetToken API.")

        val packageOverride =
            serviceConfigurationConstructor.reserveLocal("packageOverride", Type.String)

        serviceConfigurationConstructor.before {
            local(packageOverride).assign(
                call(
                    GmsCoreSupport.servicePackageOverride, paramOfType(Type.String), string(vendor)
                )
            )

            whenNotNull(local(packageOverride)) {
                paramOfType(Type.Boolean).assign(bool(false))
            }
        }

        servicePackage.captureAs("packageName").after {
            whenNotNull(local(packageOverride)) {
                capture("packageName").assign(local(packageOverride))
            }
        }
        log.info("GmsCore: ${serviceConfigurationConstructor.descriptor} selects the GmsCore package for authentication.")

        val accountTypes = googleAccountTypes.all
        check(accountTypes.isNotEmpty()) { "Google account types not found" }

        for (target in accountTypes) {
            check(target.replaceAllStrings("com.google", vendor) > 0)
            log.info("GmsCore: ${target.descriptor} uses the GmsCore account type.")
        }

        // As in Photos, authenticate with the original app identity instead of the bundle signer.
        manifest.addPermission("org.microg.gms.permission.FAKE_PACKAGE_SIGNATURE")
        manifest.addPermission("$vendor.org.microg.gms.EXTENDED_ACCESS")

        manifest.edit {
            val queries = findByTag("queries").singleOrNull()
                ?: createElement("queries").also { root.appendChild(it) }

            queries.appendChild(createElement("package").apply {
                this["android:name"] = gmsPackage
            })

            val application =
                findByTag("application").singleOrNull() ?: error("TuRuta Application is missing")

            for ((name, value) in mapOf(
                "$gmsPackage.SPOOFED_PACKAGE_NAME" to "pe.tumicro.android",
                "$gmsPackage.SPOOFED_PACKAGE_SIGNATURE" to "1b7332fc3a1629b363b78908b199ba6852fc6fdd",
                "$vendor.MICROG_PACKAGE_NAME" to gmsPackage,
            )) application.appendChild(createElement("meta-data").apply {
                this["android:name"] = name
                this["android:value"] = value
            })
        }

        mainActivityCreate.after { call(GmsCoreSupport.check, thisObject, string(gmsPackage)) }
        log.info("GmsCore: ${mainActivityCreate.descriptor} checks that $gmsPackage is installed.")
    }
}
