// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.capcut.integrity

import app.reseam.patch.*
import top.madkarma.capcut.integrity.bindings.DeviceRegistration
import top.madkarma.capcut.integrity.bindings.UnsafePackageNotice
import top.madkarma.patches.universal.signature.spoofApkFileAccess
import top.madkarma.universal.signature.bindings.SignatureKiller
import java.security.MessageDigest
import java.util.*

private const val INTERCEPTOR_CHAIN = $$"com.bytedance.retrofit2.intercept.Interceptor$Chain"
private const val RETROFIT_REQUEST = "com.bytedance.retrofit2.client.Request"
private const val JSON_OBJECT = "org.json.JSONObject"

private const val TEMPLATE_PORTAL =
    "capcut://lynxview?channel=image_lynx_template_portal&bundle=template.js"

val bypassIntegrityChecks = patch("Bypass integrity checks") {
    description("Spoofs the original signature and registers a fresh device identity to avoid device bans.")
    compatibleWith("com.lemon.lvoverseas")
    dependsOn(spoofApkFileAccess)

    execute {
        metaSecLibraryLoad.after {
            call(SignatureKiller.redirectRawApkOpens, string("libmetasec_ov.so"))
        }
        log.info("Integrity: ${metaSecLibraryLoad.descriptor} installs a process-wide seccomp redirect for MetaSec raw openat syscalls after loading the library.")

        deviceRegistrationInit.before {
            call(DeviceRegistration.initialize, paramOfType("android.content.Context"))
        }
        // The manager exists here, but its worker has not loaded or announced the previous IDs yet.
        deviceRegistrationStart.before {
            call(DeviceRegistration.resetIfNeeded)
        }
        log.info("Registration: ${deviceRegistrationInit.descriptor} initializes this installation's profile.")
        log.info("Registration: ${deviceRegistrationStart.descriptor} clears previous IDs before the worker starts without changing privacy mode.")

        deviceRegistrationRequest.before {
            val payload = call(
                DeviceRegistration.preparePayload,
                paramOfType(Type.String),
                paramOfType(JSON_OBJECT)
            )

            paramOfType(Type.String).assign(payload)
        }
        log.info("Registration: ${deviceRegistrationRequest.descriptor} registers identifiers scoped to this installation.")

        excludeAppRequestHeaders.before {
            whenTrue(
                call(
                    DeviceRegistration.excludeAppHeaders, paramOfType(RETROFIT_REQUEST)
                )
            ) { returnTrue() }
        }
        log.info("Registration: ${excludeAppRequestHeaders.descriptor} excludes old app headers from registration requests.")

        networkCommonParameters.before {
            val url = paramOfType(Type.String)
            whenTrue(call(DeviceRegistration.isRegistration, url)) { returnValue(url) }
        }
        log.info("Registration: ${networkCommonParameters.descriptor} preserves the registration URL without previous device parameters.")

        // ART profiles carry original DEX checksums and cannot be reused after these edits.
        for (component in files.components()) {
            val componentFiles = files.component(component)

            val profiles = componentFiles.list().filter {
                it.startsWith("assets/") && (it.endsWith("/baseline.prof") || it.endsWith("/baseline.profm"))
            }

            for (profile in profiles) {
                componentFiles.delete(profile)
                log.info("Integrity: removed stale ART profile $component/$profile.")
            }
        }

        val certificate = files.signers().firstOrNull()
            ?: error("The input APK has no original v2/v3 signing certificate")

        val certificateDigest = HexFormat.ofDelimiter(":").withUpperCase()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(certificate))

        packageSignature.alwaysReturn(certificateDigest)
        log.info("Integrity: ${packageSignature.descriptor} reports the original signing certificate digest.")

        val encodedCertificate = Base64.getEncoder().encodeToString(certificate)
        nativeSigningCertificate.replace {
            returnValue(
                callStatic(
                    "android.util.Base64", "decode", proto("[B", Type.String, Type.Int),
                    string(encodedCertificate), int(0),
                )
            )
        }
        log.info("Integrity: ${nativeSigningCertificate.descriptor} supplies the original certificate to the native SDK.")

        val registrationDigest =
            HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(certificate))

        registrationSignature.after {
            val payload = paramOfType(JSON_OBJECT)
            whenNotNull(payload) {
                payload.callVirtual(
                    JSON_OBJECT, "put", proto(JSON_OBJECT, Type.String, Type.Object),
                    string("sig_hash"), string(registrationDigest),
                )
            }
        }
        log.info("Integrity: ${registrationSignature.descriptor} registers the original certificate digest.")

        legacyRegistrationSignature.alwaysReturn(registrationDigest)
        log.info("Integrity: ${legacyRegistrationSignature.descriptor} reports the same original certificate digest.")

        for (callback in listOf(signatureBlacklist, painterSignatureReport, securityDialog)) {
            callback.replace { returnValue(staticField(kotlinUnit)) }
            log.info("Integrity: ${callback.descriptor} skips the signature blacklist, report, or security dialog callback.")
        }

        abnormalPackageToast.alwaysReturn()
        log.info("Integrity: ${abnormalPackageToast.descriptor} suppresses the modified-package toast.")

        injectionDetectionEnabled.alwaysReturn(false)
        log.info("Integrity: ${injectionDetectionEnabled.descriptor} disables injection detection.")

        persistCrackingVerdict.alwaysReturn()
        log.info("Integrity: ${persistCrackingVerdict.descriptor} stops persisting modified-package verdicts.")

        for (interceptor in listOf(networkIntegrityInterceptor, templateIntegrityInterceptor)) {
            interceptor.replace {
                val chain = paramOfType(INTERCEPTOR_CHAIN)

                val request =
                    chain.callInterface(INTERCEPTOR_CHAIN, "request", proto(RETROFIT_REQUEST))

                val response = chain.callInterface(
                    INTERCEPTOR_CHAIN,
                    "proceed",
                    proto("com.bytedance.retrofit2.SsResponse", RETROFIT_REQUEST),
                    request,
                )

                returnValue(response)
            }
            log.info("Integrity: ${interceptor.descriptor} preserves the response without local cracking handling.")
        }

        // Downloaded home-page JavaScript opens this notice independently of the interceptors.
        openLynxView.before {
            whenTrue(UnsafePackageNotice.isUnsafePackageModal(paramOfType(Type.String))) { returnVoid() }
        }
        log.info("Integrity: ${openLynxView.descriptor} blocks the unsafe-package notice from downloaded JavaScript.")

        val replacedUrls =
            unsafePackageFallback.method.replaceAllStrings(unsafePackageModal, TEMPLATE_PORTAL)
        check(replacedUrls > 0) { "Unsafe-package fallback URL was not found" }
        log.info("Integrity: ${unsafePackageFallback.descriptor} uses the template portal fallback.")
    }
}
