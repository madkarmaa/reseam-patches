// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.truecaller.gms

import app.reseam.patch.*
import top.madkarma.patches.shared.gmscore.signInThroughGmsCore
import top.madkarma.patches.shared.originalSignerSha1
import top.madkarma.truecaller.gms.bindings.*

val gmsCoreSupport =
    patch("GmsCore support") {
        description("Signs in with Google through separately installed Reseam GmsCore.")
        compatibleWith("com.truecaller")

        val vendorGroupId =
            stringOption(
                "gmsCoreVendorGroupId",
                title = "GmsCore vendor group ID",
                default = "app.reseam",
                required = true,
            )

        execute {
            val vendor = options[vendorGroupId]
            require(
                Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+").matches(vendor) &&
                    !vendor.endsWith(
                        ".android.gms",
                    ),
            ) {
                "gmsCoreVendorGroupId must be a vendor group such as app.reseam"
            }
            val gmsPackage = "$vendor.android.gms"

            signInThroughGmsCore(vendor)

            // These SDK classes are obfuscated. Expose only their types and options to the
            // extension.
            credentialRequest.classDef.addInterface(CredentialRequest.descriptor)
            credentialRequest.addMethod(
                CredentialRequest.reseamOptions.name,
                CredentialRequest.reseamOptions.proto,
            ) {
                returnValue(thisObject.field(credentialOptions))
            }
            log.info(
                "GmsCore: ${credentialRequest.descriptor} exposes credential options to the extension.",
            )

            for (option in googleOptions) {
                option.classDef.addInterface(GoogleOption.descriptor)
                log.info("GmsCore: ${option.descriptor} marks a Google credential option.")
            }

            val factory = klass(bundledCredentialProvider.owner)
            factory.classDef.addInterface(CredentialProviderFactory.descriptor)
            val originalSelection =
                selectCredentialProvider.method.clone("reseamOriginalSelectCredentialProvider")
            val selectOriginalProvider =
                methodTarget("selectOriginalProvider") { originalSelection }

            factory.addMethod(
                CredentialProviderFactory.reseamBundledProvider.name,
                CredentialProviderFactory.reseamBundledProvider.proto,
            ) {
                returnValue(thisObject.call(bundledCredentialProvider))
            }
            factory.addMethod(
                CredentialProviderFactory.reseamDefaultProvider.name,
                CredentialProviderFactory.reseamDefaultProvider.proto,
            ) {
                returnValue(call(selectOriginalProvider, thisObject, paramOfType(Type.Object)))
            }

            selectCredentialProvider.replace {
                returnValue(
                    Credentials.selectProvider(
                            paramOfType(factory.descriptor)
                                .cast(CredentialProviderFactory.descriptor),
                            paramOfType(Type.Object),
                        )
                        .cast(selectCredentialProvider.returnType),
                )
            }
            log.info(
                "GmsCore: ${selectCredentialProvider.descriptor} uses the bundled provider for Google sign-in.",
            )

            // Keep this override local to the request; the SDK also uses this check to clear
            // credentials.
            getBundledCredential
                .point {
                    calls(credentialProviderAvailability)
                    then { resultOf() }
                }
                .captureAs("available")
                .after {
                    val available = capture("available")
                    available.assign(
                        Credentials.useModernCredentials(
                            available,
                            paramOfType(credentialRequest.descriptor),
                        ),
                    )
                }
            log.info(
                "GmsCore: ${getBundledCredential.descriptor} uses legacy Google sign-in decoding.",
            )

            serviceEndpointConstructor.before {
                // These two String parameters are the destination package and service action.
                param(0).assign(GmsCoreSupport.endpointPackage(param(0), param(1)))
                val dynamicLookup = paramOfType(Type.Boolean)
                dynamicLookup.assign(GmsCoreSupport.dynamicLookup(dynamicLookup, param(1)))
                param(1).assign(GmsCoreSupport.endpointAction(param(1)))
            }
            log.info(
                "GmsCore: ${serviceEndpointConstructor.descriptor} sends authentication requests to $gmsPackage.",
            )

            requiresPlayServices.after {
                returnValue(
                    GmsCoreSupport.requiresPlayServices(result, thisObject.call(serviceAction)),
                )
            }
            log.info(
                "GmsCore: ${requiresPlayServices.descriptor} accepts GmsCore for Google sign-in.",
            )

            playServicesAvailability.after {
                returnValue(GmsCoreSupport.check(paramOfType(Type.Context), result))
            }
            log.info(
                "GmsCore: ${playServicesAvailability.descriptor} accepts GmsCore when Play Services is unavailable.",
            )

            val signature = originalSignerSha1()
            log.info("GmsCore: spoofs the original signing certificate $signature.")

            manifest.addPermission("org.microg.gms.permission.FAKE_PACKAGE_SIGNATURE")
            manifest.addPermission("$vendor.org.microg.gms.EXTENDED_ACCESS")

            manifest.edit {
                val queries =
                    findByTag("queries").singleOrNull()
                        ?: createElement("queries").also { root.appendChild(it) }

                queries.appendChild(
                    createElement("package").apply {
                        this["android:name"] = gmsPackage
                    },
                )

                val application = findByTag("application").single()

                for ((name, value) in
                    mapOf(
                        "$gmsPackage.SPOOFED_PACKAGE_NAME" to "com.truecaller",
                        "$gmsPackage.SPOOFED_PACKAGE_SIGNATURE" to signature,
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
