// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.integrity

import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.klass
import app.reseam.patch.method

private val metaSecInit = klass("com.bytedance.mobsec.metasec.ov.MSManagerUtils").method("init")

private val metaSecRegistration = method("metaSecRegistration") {
    calledBy(metaSecInit)
    strings("metasec_ov")
}

private val metaSecBootstrap = method("metaSecBootstrap") {
    calledBy(metaSecRegistration)
    inClass(klass(metaSecRegistration.owner))
}

internal val metaSecLibraryLoad = method("metaSecLibraryLoad") {
    calledBy(metaSecBootstrap)
    strings("Beginning load of %s...")
}

internal val packageSignature = method("packageSignature") {
    inClass(klass("com.lm.components.utils.AppUtils"))
    strings("SHA256")
}

// The native SDK's certificate callback has no retained name or diagnostic string.
internal val nativeSigningCertificate = method("nativeSigningCertificate") {
    returns(Type.Object)
    hasParam(Type.Long)
    calls { owner("android.content.pm.Signature"); name("toByteArray") }
}

internal val registrationSignature = method("registrationSignature") {
    inClass(klass("com.bytedance.bdinstall.loader.SigHashLoader"))
    strings("sig_hash")
}

internal val legacyRegistrationSignature =
    klass("com.ss.android.deviceregister.base.RegistrationHeaderHelper").method("getSigHash")

internal val signatureBlacklist = method("signatureBlacklist") {
    strings("sig_bl")
}

internal val painterSignatureReport = method("painterSignatureReport") {
    strings("java_signature", "native_signature")
}

internal val abnormalPackageToast = klass("com.xt.retouch.baseui.JniToast").method("showApkError")

internal val injectionDetectionEnabled = method("injectionDetectionEnabled") {
    inClass(klass("com.vega.launcher.godzilla.GodzillaHookedTagManager"))
    returns(Type.Boolean)
    calls {
        owner("com.vega.launcher.godzilla.InjectDetectConfig")
        returns(Type.Boolean)
    }
}

internal val persistCrackingVerdict = method("persistCrackingVerdict") {
    strings("isCracking", "crackCode")
}

internal val securityDialog = method("securityDialog") {
    inClass(klass($$"com.vega.launcher.network.interceptors.CrackingInterceptor$showGlobalSecurityDialog$1"))
    strings("lynx_overlay_id")
}

internal val templateIntegrityInterceptor =
    klass("com.xt.retouch.applauncher.core.interceptor.CrackingInterceptor").method("intercept")

internal val networkIntegrityInterceptor = klass(persistCrackingVerdict.owner).method("intercept")

internal val openLynxView =
    klass("com.lm.components.lynx.bridge.handler.LynxCommonBridge").method("viewOpen")

internal const val unsafePackageModal =
    "capcut://main/lynx?channel=image_lynx_global_unsafe_pkg_modal&bundle=template.js&hide_loading=0&hide_nav_bar=1&immersive_mode=1&enter_mode=modal&present_style=over_full_screen&loading_bgcolor=00000000&dynamic=3&exit_mode=none"

internal val unsafePackageFallback = method("unsafePackageFallback") {
    inClass(klass("com.vega.lynx.LynxSchemaConfig"))
    strings(unsafePackageModal)
}

internal val kotlinUnit = klass("kotlin.Unit").field("INSTANCE")
