// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("HiddenPackagesApi33") @file:Suppress("unused")

package dev.secp192k1.universal.hidepackages

import android.content.ComponentName
import android.content.Intent
import android.content.pm.*
import android.content.pm.PackageManager.*
import app.reseam.runtime.Export

// Keep newer Android calls separate so older devices can verify the base class.
@Export
fun getPackageInfo(
    packageManager: PackageManager,
    packageName: String,
    flags: PackageInfoFlags,
): PackageInfo {
    requireVisible(packageName)
    return packageManager.getPackageInfo(packageName, flags)
}

@Export(name = "getVersionedPackageInfo")
fun getPackageInfo(
    packageManager: PackageManager,
    versionedPackage: VersionedPackage,
    flags: PackageInfoFlags,
): PackageInfo {
    requireVisible(versionedPackage.packageName)
    return packageManager.getPackageInfo(versionedPackage, flags)
}

@Export
fun getApplicationInfo(
    packageManager: PackageManager,
    packageName: String,
    flags: ApplicationInfoFlags,
): ApplicationInfo {
    requireVisible(packageName)
    return packageManager.getApplicationInfo(packageName, flags)
}

@Export
fun getPackageUid(
    packageManager: PackageManager,
    packageName: String,
    flags: PackageInfoFlags,
): Int {
    requireVisible(packageName)
    return packageManager.getPackageUid(packageName, flags)
}

@Export
fun getPackageGids(
    packageManager: PackageManager,
    packageName: String,
    flags: PackageInfoFlags,
): IntArray? {
    requireVisible(packageName)
    return packageManager.getPackageGids(packageName, flags)
}

@Export
fun getInstalledPackages(
    packageManager: PackageManager,
    flags: PackageInfoFlags,
): List<PackageInfo>? {
    return visible(packageManager.getInstalledPackages(flags), PACKAGE_INFO)
}

@Export
fun getInstalledApplications(
    packageManager: PackageManager,
    flags: ApplicationInfoFlags,
): List<ApplicationInfo>? {
    return visible(packageManager.getInstalledApplications(flags), APPLICATION_INFO)
}

@Export
fun getPackagesHoldingPermissions(
    packageManager: PackageManager,
    permissions: Array<String?>,
    flags: PackageInfoFlags,
): List<PackageInfo>? {
    return visible(
        packageManager.getPackagesHoldingPermissions(permissions, flags),
        PACKAGE_INFO,
    )
}

@Export
fun queryIntentActivities(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentActivities(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentServices(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentServices(intent, flags), RESOLVE_INFO)
}

@Export
fun queryBroadcastReceivers(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): List<ResolveInfo>? {
    return visible(packageManager.queryBroadcastReceivers(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentContentProviders(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentContentProviders(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentActivityOptions(
    packageManager: PackageManager,
    caller: ComponentName?,
    specifics: List<Intent?>?,
    intent: Intent,
    flags: ResolveInfoFlags,
): List<ResolveInfo>? {
    return visible(
        packageManager.queryIntentActivityOptions(caller, specifics, intent, flags),
        RESOLVE_INFO,
    )
}

@Export
fun resolveActivity(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): ResolveInfo? {
    return visible(packageManager.resolveActivity(intent, flags))
}

@Export
fun resolveService(
    packageManager: PackageManager,
    intent: Intent,
    flags: ResolveInfoFlags,
): ResolveInfo? {
    return visible(packageManager.resolveService(intent, flags))
}

@Export
fun resolveContentProvider(
    packageManager: PackageManager,
    authority: String,
    flags: ComponentInfoFlags,
): ProviderInfo? {
    return visible(packageManager.resolveContentProvider(authority, flags))
}

@Export
fun queryContentProviders(
    packageManager: PackageManager,
    processName: String?,
    packageUid: Int,
    flags: ComponentInfoFlags,
): List<ProviderInfo>? {
    return visible(
        packageManager.queryContentProviders(processName, packageUid, flags),
        PROVIDER_INFO,
    )
}

@Export
fun getActivityInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: ComponentInfoFlags,
): ActivityInfo {
    requireVisible(component)
    return packageManager.getActivityInfo(component, flags)
}

@Export
fun getServiceInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: ComponentInfoFlags,
): ServiceInfo {
    requireVisible(component)
    return packageManager.getServiceInfo(component, flags)
}

@Export
fun getReceiverInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: ComponentInfoFlags,
): ActivityInfo {
    requireVisible(component)
    return packageManager.getReceiverInfo(component, flags)
}

@Export
fun getProviderInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: ComponentInfoFlags,
): ProviderInfo {
    requireVisible(component)
    return packageManager.getProviderInfo(component, flags)
}

@Export
fun canPackageQuery(
    packageManager: PackageManager,
    sourcePackageName: String,
    targetPackageName: String,
): Boolean {
    requireVisible(sourcePackageName)
    requireVisible(targetPackageName)
    return packageManager.canPackageQuery(sourcePackageName, targetPackageName)
}
