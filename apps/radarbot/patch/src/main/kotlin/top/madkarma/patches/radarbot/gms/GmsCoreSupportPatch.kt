// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.radarbot.gms

import app.reseam.patch.*
import top.madkarma.patches.radarbot.RadarbotPatchStrings
import top.madkarma.patches.radarbot.shared.radarbotIntegrity
import top.madkarma.patches.shared.gmscore.signInThroughGmsCore
import top.madkarma.patches.shared.originalSignerSha1
import top.madkarma.shared.gmscore.bindings.GmsCore

private const val RADARBOT = "com.vialsoft.radarbot_free"

// Google Play services identifies the re-signed app by its installed certificate and refuses its
// sign-in, so Radarbot's Google login goes through GmsCore under the original identity.
val gmsCoreSupport =
    patch(RadarbotPatchStrings.gmsCoreSupportName) {
        description(RadarbotPatchStrings.gmsCoreSupportDescription)
        compatibleWith(RADARBOT)
        dependsOn(radarbotIntegrity)

        val vendorGroupId =
            stringOption(
                "gmsCoreVendorGroupId",
                title = RadarbotPatchStrings.gmsCoreSupportGmsCoreVendorGroupIdTitle,
                default = "app.reseam",
                required = true,
            )

        execute {
            val vendor = options[vendorGroupId]
            require(
                Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+").matches(vendor) &&
                    !vendor.endsWith(".android.gms"),
            ) {
                "gmsCoreVendorGroupId must be a vendor group such as app.reseam"
            }
            val gmsPackage = "$vendor.android.gms"

            signInThroughGmsCore(vendor)

            serviceConfigurationConstructor.before {
                // The client passes the destination package, service action, and dynamic-lookup
                // flag.
                val packageOverride = call(GmsCore.servicePackageOverride, param(1))

                whenNotNull(packageOverride) {
                    param(0).assign(packageOverride)
                    param(1).assign(call(GmsCore.serviceAction, param(1)))
                    paramOfType(Type.Boolean).assign(bool(false))
                }
            }
            log.info(
                "GmsCore: ${serviceConfigurationConstructor.descriptor} binds authentication services directly to $gmsPackage.",
            )

            requiresPlayServices.before {
                whenNotNull(call(GmsCore.servicePackageOverride, thisObject.call(serviceAction))) {
                    returnFalse()
                }
            }
            log.info(
                "GmsCore: ${requiresPlayServices.descriptor} accepts GmsCore for authentication clients.",
            )

            // Android 14+ otherwise selects the platform provider, which only reaches Google Play
            // services.
            bestAvailableProvider.replace {
                returnValue(thisObject.call(manifestCredentialProvider))
            }
            log.info(
                "GmsCore: ${bestAvailableProvider.descriptor} selects the bundled AndroidX credential provider.",
            )

            // GmsCore returns the pre-Android 14 Bundle format, which the platform readers miss.
            for ((platformReader, bundleReader) in
                listOf(
                    platformResponseReader to bundleResponse,
                    platformExceptionReader to bundleException,
                )) {
                platformReader.after {
                    whenNull(result) { returnValue(call(bundleReader, param(0))) }
                }
                log.info(
                    "GmsCore: ${platformReader.descriptor} falls back to ${bundleReader.descriptor} for GmsCore results.",
                )
            }

            val availabilityChecks = credentialsAvailable.all
            check(availabilityChecks.isNotEmpty()) {
                "Credential provider availability checks not found"
            }
            for (target in availabilityChecks) {
                target.alwaysReturn(true)
                log.info("GmsCore: ${target.descriptor} enables credentials through GmsCore.")
            }

            val accountTypes = googleAccountTypes.all
            check(accountTypes.isNotEmpty()) { "Google account types not found" }
            for (target in accountTypes) {
                check(target.method.replaceAllStrings("com.google", vendor) > 0)
                log.info("GmsCore: ${target.descriptor} uses the GmsCore account type.")
            }

            // GmsCore signs in as the app Google knows, not as the re-signed build.
            val originalSignature = originalSignerSha1()
            log.info("GmsCore: presents the original signing certificate $originalSignature.")

            manifest.addPermission("org.microg.gms.permission.FAKE_PACKAGE_SIGNATURE")
            manifest.addPermission("$vendor.org.microg.gms.EXTENDED_ACCESS")

            manifest.edit {
                val queries =
                    findByTag("queries").singleOrNull()
                        ?: createElement("queries").also { root.appendChild(it) }
                queries.appendChild(
                    createElement("package").apply { this["android:name"] = gmsPackage },
                )

                val application =
                    findByTag("application").singleOrNull()
                        ?: error("Radarbot: missing <application>")
                for ((name, value) in
                    mapOf(
                        "$gmsPackage.SPOOFED_PACKAGE_NAME" to RADARBOT,
                        "$gmsPackage.SPOOFED_PACKAGE_SIGNATURE" to originalSignature,
                        "$vendor.MICROG_PACKAGE_NAME" to gmsPackage,
                    )) application.appendChild(
                    createElement("meta-data").apply {
                        this["android:name"] = name
                        this["android:value"] = value
                    },
                )
            }
        }
    }
