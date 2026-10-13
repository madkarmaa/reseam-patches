// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package dev.secp192k1.patches.universal

import app.reseam.patch.ExtClass
import app.reseam.patch.ExtMethod
import app.reseam.patch.Type
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

internal const val PACKAGE_MANAGER = "android.content.pm.PackageManager"

private const val EXTENSION_PACKAGE = "dev.secp192k1.universal.hidepackages"
private const val PACKAGE_INFO = "android.content.pm.PackageInfo"
private const val APPLICATION_INFO = "android.content.pm.ApplicationInfo"
private const val ACTIVITY_INFO = "android.content.pm.ActivityInfo"
private const val SERVICE_INFO = "android.content.pm.ServiceInfo"
private const val PROVIDER_INFO = "android.content.pm.ProviderInfo"
private const val RESOLVE_INFO = "android.content.pm.ResolveInfo"
private const val VERSIONED_PACKAGE = "android.content.pm.VersionedPackage"
private const val INSTALL_SOURCE_INFO = "android.content.pm.InstallSourceInfo"
private const val PACKAGE_INFO_FLAGS = $$"android.content.pm.PackageManager$PackageInfoFlags"
private const val APPLICATION_INFO_FLAGS =
    $$"android.content.pm.PackageManager$ApplicationInfoFlags"
private const val RESOLVE_INFO_FLAGS = $$"android.content.pm.PackageManager$ResolveInfoFlags"
private const val COMPONENT_INFO_FLAGS = $$"android.content.pm.PackageManager$ComponentInfoFlags"
private const val COMPONENT_NAME = "android.content.ComponentName"
private const val INTENT = "android.content.Intent"
private const val INTENT_ARRAY = "android.content.Intent[]"
private const val STRING_ARRAY = "java.lang.String[]"
private const val INT_ARRAY = "[I"
private const val BYTE_ARRAY = "[B"
private const val DRAWABLE = "android.graphics.drawable.Drawable"
private const val RESOURCES = "android.content.res.Resources"

internal abstract class PackageManagerWrappers(simpleName: String) :
    ExtClass("$EXTENSION_PACKAGE.$simpleName") {

    private val declaredWrappers = mutableListOf<ExtMethod>()

    val wrappers: List<ExtMethod>
        get() = declaredWrappers

    // Register each binding when declared, so overloads need no separate redirection list.
    protected fun wrapper(vararg parameters: String, returns: String, name: String? = null) =
        PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ExtMethod>> { thisReference, property ->
            val wrapper = static(
                PACKAGE_MANAGER, *parameters, returns = returns, name = name
            ).provideDelegate(thisReference, property).getValue(thisReference, property)

            declaredWrappers += wrapper

            ReadOnlyProperty { _, _ -> wrapper }
        }
}

internal object HiddenPackages : PackageManagerWrappers("HiddenPackages") {
    val initialize by static(Type.String)

    val getPackageInfo by wrapper(Type.String, Type.Int, returns = PACKAGE_INFO)
    val getVersionedPackageInfo by wrapper(
        VERSIONED_PACKAGE, Type.Int, returns = PACKAGE_INFO, name = "getPackageInfo"
    )
    val getApplicationInfo by wrapper(Type.String, Type.Int, returns = APPLICATION_INFO)
    val getPackageUid by wrapper(Type.String, Type.Int, returns = Type.Int)
    val getPackageGids by wrapper(Type.String, returns = INT_ARRAY)
    val getPackageGidsWithFlags by wrapper(
        Type.String, Type.Int, returns = INT_ARRAY, name = "getPackageGids"
    )
    val getLaunchIntentForPackage by wrapper(Type.String, returns = INTENT)
    val getLeanbackLaunchIntentForPackage by wrapper(Type.String, returns = INTENT)
    val getApplicationIcon by wrapper(Type.String, returns = DRAWABLE)
    val getApplicationBanner by wrapper(Type.String, returns = DRAWABLE)
    val getApplicationLogo by wrapper(Type.String, returns = DRAWABLE)
    val getResourcesForApplication by wrapper(Type.String, returns = RESOURCES)
    val getDrawable by wrapper(Type.String, Type.Int, APPLICATION_INFO, returns = DRAWABLE)
    val getText by wrapper(Type.String, Type.Int, APPLICATION_INFO, returns = Type.CharSequence)
    val getApplicationEnabledSetting by wrapper(Type.String, returns = Type.Int)
    val getInstallerPackageName by wrapper(Type.String, returns = Type.String)
    val checkSignatures by wrapper(Type.String, Type.String, returns = Type.Int)
    val checkPermission by wrapper(Type.String, Type.String, returns = Type.Int)
    val getPackagesForUid by wrapper(Type.Int, returns = STRING_ARRAY)
    val getNameForUid by wrapper(Type.Int, returns = Type.String)
    val getInstalledPackages by wrapper(Type.Int, returns = Type.List)
    val getInstalledApplications by wrapper(Type.Int, returns = Type.List)
    val getPackagesHoldingPermissions by wrapper(STRING_ARRAY, Type.Int, returns = Type.List)
    val queryIntentActivities by wrapper(INTENT, Type.Int, returns = Type.List)
    val queryIntentServices by wrapper(INTENT, Type.Int, returns = Type.List)
    val queryBroadcastReceivers by wrapper(INTENT, Type.Int, returns = Type.List)
    val queryIntentContentProviders by wrapper(INTENT, Type.Int, returns = Type.List)
    val queryIntentActivityOptions by wrapper(
        COMPONENT_NAME, INTENT_ARRAY, INTENT, Type.Int, returns = Type.List
    )
    val resolveActivity by wrapper(INTENT, Type.Int, returns = RESOLVE_INFO)
    val resolveService by wrapper(INTENT, Type.Int, returns = RESOLVE_INFO)
    val resolveContentProvider by wrapper(Type.String, Type.Int, returns = PROVIDER_INFO)
    val queryContentProviders by wrapper(Type.String, Type.Int, Type.Int, returns = Type.List)
    val getActivityInfo by wrapper(COMPONENT_NAME, Type.Int, returns = ACTIVITY_INFO)
    val getServiceInfo by wrapper(COMPONENT_NAME, Type.Int, returns = SERVICE_INFO)
    val getReceiverInfo by wrapper(COMPONENT_NAME, Type.Int, returns = ACTIVITY_INFO)
    val getProviderInfo by wrapper(COMPONENT_NAME, Type.Int, returns = PROVIDER_INFO)
    val getComponentEnabledSetting by wrapper(COMPONENT_NAME, returns = Type.Int)
}

internal object HiddenPackagesApi28 : PackageManagerWrappers("HiddenPackagesApi28") {
    val hasSigningCertificate by wrapper(Type.String, BYTE_ARRAY, Type.Int, returns = Type.Boolean)
    val hasSigningCertificateForUid by wrapper(
        Type.Int, BYTE_ARRAY, Type.Int, returns = Type.Boolean, name = "hasSigningCertificate"
    )
}

internal object HiddenPackagesApi29 : PackageManagerWrappers("HiddenPackagesApi29") {
    val isPackageSuspended by wrapper(Type.String, returns = Type.Boolean)
}

internal object HiddenPackagesApi30 : PackageManagerWrappers("HiddenPackagesApi30") {
    val getInstallSourceInfo by wrapper(Type.String, returns = INSTALL_SOURCE_INFO)
}

internal object HiddenPackagesApi31 : PackageManagerWrappers("HiddenPackagesApi31") {
    val getTargetSdkVersion by wrapper(Type.String, returns = Type.Int)
}

internal object HiddenPackagesApi33 : PackageManagerWrappers("HiddenPackagesApi33") {
    val getPackageInfo by wrapper(Type.String, PACKAGE_INFO_FLAGS, returns = PACKAGE_INFO)
    val getVersionedPackageInfo by wrapper(
        VERSIONED_PACKAGE, PACKAGE_INFO_FLAGS, returns = PACKAGE_INFO, name = "getPackageInfo"
    )
    val getApplicationInfo by wrapper(
        Type.String, APPLICATION_INFO_FLAGS, returns = APPLICATION_INFO
    )
    val getPackageUid by wrapper(Type.String, PACKAGE_INFO_FLAGS, returns = Type.Int)
    val getPackageGids by wrapper(Type.String, PACKAGE_INFO_FLAGS, returns = INT_ARRAY)
    val getInstalledPackages by wrapper(PACKAGE_INFO_FLAGS, returns = Type.List)
    val getInstalledApplications by wrapper(APPLICATION_INFO_FLAGS, returns = Type.List)
    val getPackagesHoldingPermissions by wrapper(
        STRING_ARRAY, PACKAGE_INFO_FLAGS, returns = Type.List
    )
    val queryIntentActivities by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = Type.List)
    val queryIntentServices by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = Type.List)
    val queryBroadcastReceivers by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = Type.List)
    val queryIntentContentProviders by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = Type.List)
    val queryIntentActivityOptions by wrapper(
        COMPONENT_NAME, Type.List, INTENT, RESOLVE_INFO_FLAGS, returns = Type.List
    )
    val resolveActivity by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = RESOLVE_INFO)
    val resolveService by wrapper(INTENT, RESOLVE_INFO_FLAGS, returns = RESOLVE_INFO)
    val resolveContentProvider by wrapper(
        Type.String, COMPONENT_INFO_FLAGS, returns = PROVIDER_INFO
    )
    val queryContentProviders by wrapper(
        Type.String, Type.Int, COMPONENT_INFO_FLAGS, returns = Type.List
    )
    val getActivityInfo by wrapper(COMPONENT_NAME, COMPONENT_INFO_FLAGS, returns = ACTIVITY_INFO)
    val getServiceInfo by wrapper(COMPONENT_NAME, COMPONENT_INFO_FLAGS, returns = SERVICE_INFO)
    val getReceiverInfo by wrapper(COMPONENT_NAME, COMPONENT_INFO_FLAGS, returns = ACTIVITY_INFO)
    val getProviderInfo by wrapper(COMPONENT_NAME, COMPONENT_INFO_FLAGS, returns = PROVIDER_INFO)
    val canPackageQuery by wrapper(Type.String, Type.String, returns = Type.Boolean)
}

internal val packageManagerWrappers = listOf(
    HiddenPackages,
    HiddenPackagesApi28,
    HiddenPackagesApi29,
    HiddenPackagesApi30,
    HiddenPackagesApi31,
    HiddenPackagesApi33,
).flatMap { it.wrappers }
