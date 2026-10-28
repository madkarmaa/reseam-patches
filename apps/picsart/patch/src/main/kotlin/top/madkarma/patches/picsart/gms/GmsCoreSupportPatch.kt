// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.picsart.gms

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.shared.originalSignerSha1
import top.madkarma.picsart.gms.bindings.GmsCoreSupport

val gmsCoreSupport = patch("GmsCore support") {
    description("Signs in with Google through separately installed Reseam GmsCore.")
    compatibleWith("com.picsart.studio")

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
        ) { "gmsCoreVendorGroupId must be a vendor group such as app.reseam" }
        val gmsPackage = "$vendor.android.gms"

        check(
            signInIntentLauncher.method.replaceAllStrings(
                "com.google.android.gms", gmsPackage
            ) > 0
        )
        log.info("GmsCore: ${signInIntentLauncher.descriptor} opens the GmsCore sign-in activity.")

        serviceConfigurationConstructor.before {
            // The constructor takes the destination package, service action, and dynamic-lookup flag.
            val packageOverride =
                call(GmsCoreSupport.servicePackageOverride, param(1), string(vendor))

            whenNotNull(packageOverride) {
                param(0).assign(packageOverride)
                paramOfType(Type.Boolean).assign(bool(false))
            }
        }
        log.info("GmsCore: ${serviceConfigurationConstructor.descriptor} binds authentication services directly to $gmsPackage.")

        requiresPlayServices.before {
            whenNotNull(
                call(
                    GmsCoreSupport.servicePackageOverride,
                    thisObject.call(serviceAction),
                    string(vendor)
                )
            ) {
                returnFalse()
            }
        }
        log.info("GmsCore: ${requiresPlayServices.descriptor} accepts GmsCore for authentication clients.")

        val accountTypes = googleAccountTypes.all
        check(accountTypes.isNotEmpty()) { "Google account types not found" }

        for (target in accountTypes) {
            check(target.method.replaceAllStrings("com.google", vendor) > 0)
            log.info("GmsCore: ${target.descriptor} uses the GmsCore account type.")
        }

        // Authenticate with the original app identity instead of the bundle signer.
        val originalSignature = originalSignerSha1()
        log.info("GmsCore: presents the original signing certificate $originalSignature.")

        manifest.addPermission("org.microg.gms.permission.FAKE_PACKAGE_SIGNATURE")
        manifest.addPermission("$vendor.org.microg.gms.EXTENDED_ACCESS")

        manifest.edit {
            val queries = findByTag("queries").singleOrNull()
                ?: createElement("queries").also { root.appendChild(it) }

            queries.appendChild(createElement("package").apply {
                this["android:name"] = gmsPackage
            })

            val application =
                findByTag("application").singleOrNull() ?: error("Picsart Application is missing")

            for ((name, value) in mapOf(
                "$gmsPackage.SPOOFED_PACKAGE_NAME" to "com.picsart.studio",
                "$gmsPackage.SPOOFED_PACKAGE_SIGNATURE" to originalSignature,
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
