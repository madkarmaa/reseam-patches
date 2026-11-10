// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: AGPL-3.0-or-later
// Reseam-derived material: see NOTICE for applicable section 7 terms.

@file:Suppress("unused")

package top.madkarma.patches.googlephotos.gms

import app.reseam.patch.*
import top.madkarma.googlephotos.gms.bindings.GmsCoreSupport
import top.madkarma.patches.googlephotos.GOOGLE_PHOTOS
import top.madkarma.patches.googlephotos.GooglePhotosPatchStrings
import top.madkarma.patches.shared.isPresent

val gmsCoreSupport =
    patch(GooglePhotosPatchStrings.gmsCoreSupportName) {
        description(
            GooglePhotosPatchStrings.gmsCoreSupportDescription,
        )
        compatibleWith(GOOGLE_PHOTOS)

        val vendorGroupId =
            stringOption(
                "gmsCoreVendorGroupId",
                title = GooglePhotosPatchStrings.gmsCoreSupportGmsCoreVendorGroupIdTitle,
                description =
                    GooglePhotosPatchStrings.gmsCoreSupportGmsCoreVendorGroupIdDescription,
                default = "app.reseam",
                required = true,
            )

        val packageName =
            stringOption(
                "packageName",
                title = GooglePhotosPatchStrings.gmsCoreSupportPackageNameTitle,
                default = "app.reseam.android.apps.photos",
                required = true,
            )

        execute {
            val vendor = options[vendorGroupId]
            val newPackage = options[packageName]
            val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")

            require(packagePattern.matches(vendor) && !vendor.endsWith(".android.gms")) {
                "gmsCoreVendorGroupId must be a vendor group such as app.reseam"
            }

            require(packagePattern.matches(newPackage) && newPackage != GOOGLE_PHOTOS) {
                "packageName must be a valid package different from the original Google Photos package"
            }

            val gmsCorePackage = "$vendor.android.gms"

            googleAccounts.replace {
                returnValue(
                    call(
                        GmsCoreSupport.readAccounts,
                        paramOfType(Type.Context),
                        string(vendor),
                    ),
                )
            }
            log.info("GmsCore: ${googleAccounts.descriptor} reads GmsCore accounts.")

            // Reuse Photos' account-visibility task without generating extension method bodies.
            appEntry {
                val client =
                    newInstance(
                        accountAccessClientConstructor.owner,
                        accountAccessClientConstructor.proto,
                        application,
                        call(GmsCoreSupport.getAccountRefreshExecutor),
                    )

                val task =
                    newInstance(
                        accountAccessTaskConstructor.owner,
                        accountAccessTaskConstructor.proto,
                        client,
                    )

                call(GmsCoreSupport.initialize, task)
            }

            registerAccountsListener.before {
                call(
                    GmsCoreSupport.rememberAccountsListener,
                    paramOfType("android.accounts.OnAccountsUpdateListener"),
                )
            }
            log.info(
                "GmsCore: ${registerAccountsListener.descriptor} registers the account refresh listener.",
            )

            accountPermissionResult.before {
                whenTrue(
                    call(
                        GmsCoreSupport.onAccountPermissionResult,
                        thisObject,
                        paramOfType(Type.Int),
                        paramOfType("[Ljava/lang/String;"),
                        paramOfType("[I"),
                        string(vendor),
                    ),
                ) {
                    returnVoid()
                }
            }
            log.info(
                "GmsCore: ${accountPermissionResult.descriptor} handles GmsCore account access results.",
            )

            val (constants, uris) = renameGmsCorePackages(vendor, newPackage)

            playServicesCheck.alwaysReturn()
            log.info(
                "GmsCore: ${playServicesCheck.descriptor} skips the Play Services requirement.",
            )

            playServicesAvailability.alwaysReturn(0)
            log.info("GmsCore: ${playServicesAvailability.descriptor} reports services available.")

            checkAccountValidity.alwaysReturn()
            log.info(
                "GmsCore: ${checkAccountValidity.descriptor} skips account validation through Play Services.",
            )

            frictionlessEligibility.replace {
                whenTrue(thisObject.field(loginServices).call(frictionlessServicesAvailable)) {
                    returnTrue()
                }
                thisObject.field(loginAccountHandler).call(restoreSelectedAccount)
                returnFalse()
            }
            log.info(
                "GmsCore: ${frictionlessEligibility.descriptor} restores the saved account when frictionless login is unavailable.",
            )

            val notificationRegistrationConstructor =
                if (isPresent(inlineIdentifierRegistration)) inlineIdentifierRegistrationConstructor
                else if (isPresent(helperIdentifierRegistration))
                    helperIdentifierRegistrationConstructor
                else error("Unsupported Google Photos notification registration")

            notificationRegistrationConstructor.before {
                val context = paramOfType(Type.Context)
                context.assign(call(GmsCoreSupport.withPackageName, context, string(GOOGLE_PHOTOS)))
            }
            log.info(
                "GmsCore: ${notificationRegistrationConstructor.descriptor} registers notifications with the original package identity.",
            )

            homeActivityOnStart.after {
                call(
                    GmsCoreSupport.check,
                    thisObject,
                    string(gmsCorePackage),
                    string(vendor),
                )
            }
            log.info(
                "GmsCore: ${homeActivityOnStart.descriptor} checks GmsCore installation and account access.",
            )

            log.info(
                "GmsCore: rewrote $constants constants and $uris URIs; package=$newPackage, vendor=$vendor.",
            )
        }
    }
