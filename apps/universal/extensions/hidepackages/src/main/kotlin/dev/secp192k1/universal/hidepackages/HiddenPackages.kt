// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("HiddenPackages") @file:Suppress("unused")

package dev.secp192k1.universal.hidepackages

import android.content.ComponentName
import android.content.Intent
import android.content.pm.*
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.graphics.drawable.Drawable
import app.reseam.runtime.Export
import java.util.*

// Keep newer Android calls separate so older devices can verify the base class.
internal val PACKAGE_INFO = PackageNameReader<PackageInfo> { it.packageName }
internal val APPLICATION_INFO = PackageNameReader<ApplicationInfo> { it.packageName }
internal val PROVIDER_INFO = PackageNameReader<ProviderInfo> { it.packageName }
internal val RESOLVE_INFO = PackageNameReader<ResolveInfo> { packageNameOf(it) }

@Volatile
private var hiddenPackageNames: Set<String> = emptySet()

@Export
fun initialize(joinedPackageNames: String) {
    if (hiddenPackageNames.isNotEmpty()) return
    hiddenPackageNames = HashSet(joinedPackageNames.split(','))
}

internal fun isHidden(packageName: String?): Boolean =
    packageName != null && packageName in hiddenPackageNames

internal fun isEveryPackageHidden(packageManager: PackageManager, packageUid: Int): Boolean {
    val packageNames = packageManager.getPackagesForUid(packageUid) ?: return false
    if (packageNames.isEmpty()) return false
    for (packageName in packageNames) if (!isHidden(packageName)) return false
    return true
}

internal fun requireVisible(packageName: String?) {
    if (isHidden(packageName)) throw NameNotFoundException(packageName)
}

internal fun requireVisible(component: ComponentName?) {
    if (component != null && isHidden(component.packageName)) throw NameNotFoundException(component.toString())
}

internal fun requireKnownPackage(packageName: String?) {
    if (isHidden(packageName)) throw IllegalArgumentException("Unknown package: $packageName")
}

private fun packageNameOf(info: ResolveInfo): String? {
    info.activityInfo?.let {
        return it.packageName
    }
    info.serviceInfo?.let {
        return it.packageName
    }
    info.providerInfo?.let {
        return it.packageName
    }
    return info.resolvePackageName
}

internal fun visible(info: ResolveInfo?): ResolveInfo? =
    if (info != null && isHidden(packageNameOf(info))) null else info

internal fun visible(info: ProviderInfo?): ProviderInfo? =
    if (info != null && isHidden(info.packageName)) null else info

// Keep the original list when no entry needs removing.
internal fun <T> visible(items: List<T>?, reader: PackageNameReader<T>): List<T>? {
    if (items == null) return null
    val size = items.size
    var firstHiddenIndex = 0
    while (firstHiddenIndex < size && !isHidden(reader.packageNameOf(items[firstHiddenIndex]))) firstHiddenIndex++
    if (firstHiddenIndex == size) return items
    val visibleItems = ArrayList<T>(size - 1)
    visibleItems.addAll(items.subList(0, firstHiddenIndex))
    for (index in firstHiddenIndex + 1 until size) {
        val item = items[index]
        if (!isHidden(reader.packageNameOf(item))) visibleItems.add(item)
    }
    return visibleItems
}

internal fun interface PackageNameReader<T> {
    fun packageNameOf(item: T): String?
}

@Export
fun getPackageInfo(
    packageManager: PackageManager,
    packageName: String,
    flags: Int,
): PackageInfo? {
    requireVisible(packageName)
    return packageManager.getPackageInfo(packageName, flags)
}

@Export(name = "getVersionedPackageInfo")
fun getPackageInfo(
    packageManager: PackageManager,
    versionedPackage: VersionedPackage,
    flags: Int,
): PackageInfo? {
    requireVisible(versionedPackage.packageName)
    return packageManager.getPackageInfo(versionedPackage, flags)
}

@Export
fun getApplicationInfo(
    packageManager: PackageManager,
    packageName: String,
    flags: Int,
): ApplicationInfo {
    requireVisible(packageName)
    return packageManager.getApplicationInfo(packageName, flags)
}

@Export
fun getPackageUid(packageManager: PackageManager, packageName: String, flags: Int): Int {
    requireVisible(packageName)
    return packageManager.getPackageUid(packageName, flags)
}

@Export
fun getPackageGids(packageManager: PackageManager, packageName: String): IntArray? {
    requireVisible(packageName)
    return packageManager.getPackageGids(packageName)
}

@Export(name = "getPackageGidsWithFlags")
fun getPackageGids(packageManager: PackageManager, packageName: String, flags: Int): IntArray? {
    requireVisible(packageName)
    return packageManager.getPackageGids(packageName, flags)
}

@Export
fun getLaunchIntentForPackage(packageManager: PackageManager, packageName: String): Intent? {
    if (isHidden(packageName)) return null
    return packageManager.getLaunchIntentForPackage(packageName)
}

@Export
fun getLeanbackLaunchIntentForPackage(
    packageManager: PackageManager,
    packageName: String,
): Intent? {
    if (isHidden(packageName)) return null
    return packageManager.getLeanbackLaunchIntentForPackage(packageName)
}

@Export
fun getApplicationIcon(packageManager: PackageManager, packageName: String): Drawable {
    requireVisible(packageName)
    return packageManager.getApplicationIcon(packageName)
}

@Export
fun getApplicationBanner(packageManager: PackageManager, packageName: String): Drawable? {
    requireVisible(packageName)
    return packageManager.getApplicationBanner(packageName)
}

@Export
fun getApplicationLogo(packageManager: PackageManager, packageName: String): Drawable? {
    requireVisible(packageName)
    return packageManager.getApplicationLogo(packageName)
}

@Export
fun getResourcesForApplication(
    packageManager: PackageManager,
    packageName: String,
): Resources {
    requireVisible(packageName)
    return packageManager.getResourcesForApplication(packageName)
}

@Export
fun getDrawable(
    packageManager: PackageManager,
    packageName: String,
    resourceId: Int,
    applicationInfo: ApplicationInfo?,
): Drawable? {
    if (isHidden(packageName)) return null
    return packageManager.getDrawable(packageName, resourceId, applicationInfo)
}

@Export
fun getText(
    packageManager: PackageManager,
    packageName: String,
    resourceId: Int,
    applicationInfo: ApplicationInfo?,
): CharSequence? {
    if (isHidden(packageName)) return null
    return packageManager.getText(packageName, resourceId, applicationInfo)
}

@Export
fun getApplicationEnabledSetting(packageManager: PackageManager, packageName: String): Int {
    requireKnownPackage(packageName)
    return packageManager.getApplicationEnabledSetting(packageName)
}

@Suppress("DEPRECATION")
@Export
fun getInstallerPackageName(packageManager: PackageManager, packageName: String): String? {
    requireKnownPackage(packageName)
    return packageManager.getInstallerPackageName(packageName)
}

@Export
fun checkSignatures(
    packageManager: PackageManager,
    firstPackageName: String,
    secondPackageName: String,
): Int {
    if (isHidden(firstPackageName) || isHidden(secondPackageName)) return PackageManager.SIGNATURE_UNKNOWN_PACKAGE
    return packageManager.checkSignatures(firstPackageName, secondPackageName)
}

@Export
fun checkPermission(
    packageManager: PackageManager,
    permissionName: String,
    packageName: String,
): Int {
    if (isHidden(packageName)) return PackageManager.PERMISSION_DENIED
    return packageManager.checkPermission(permissionName, packageName)
}

@Export
fun getPackagesForUid(packageManager: PackageManager, packageUid: Int): Array<String?>? {
    val packageNames = packageManager.getPackagesForUid(packageUid) ?: return null
    var visibleCount = 0
    for (packageName in packageNames) if (!isHidden(packageName)) visibleCount++
    if (visibleCount == packageNames.size) return packageNames
    if (visibleCount == 0) return null
    val visiblePackageNames = arrayOfNulls<String>(visibleCount)
    var index = 0
    for (packageName in packageNames) if (!isHidden(packageName)) visiblePackageNames[index++] =
        packageName
    return visiblePackageNames
}

@Export
fun getNameForUid(packageManager: PackageManager, packageUid: Int): String? {
    val name = packageManager.getNameForUid(packageUid)
    if (name == null || isHidden(name)) return null
    // Hide a shared user ID only when all its packages are hidden.
    if (name.indexOf(':') >= 0 && isEveryPackageHidden(packageManager, packageUid)) return null
    return name
}

@Export
fun getInstalledPackages(packageManager: PackageManager, flags: Int): List<PackageInfo>? {
    return visible(packageManager.getInstalledPackages(flags), PACKAGE_INFO)
}

@Export
fun getInstalledApplications(
    packageManager: PackageManager,
    flags: Int,
): List<ApplicationInfo>? {
    return visible(packageManager.getInstalledApplications(flags), APPLICATION_INFO)
}

@Export
fun getPackagesHoldingPermissions(
    packageManager: PackageManager,
    permissions: Array<String?>,
    flags: Int,
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
    flags: Int,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentActivities(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentServices(
    packageManager: PackageManager,
    intent: Intent,
    flags: Int,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentServices(intent, flags), RESOLVE_INFO)
}

@Export
fun queryBroadcastReceivers(
    packageManager: PackageManager,
    intent: Intent,
    flags: Int,
): List<ResolveInfo>? {
    return visible(packageManager.queryBroadcastReceivers(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentContentProviders(
    packageManager: PackageManager,
    intent: Intent,
    flags: Int,
): List<ResolveInfo>? {
    return visible(packageManager.queryIntentContentProviders(intent, flags), RESOLVE_INFO)
}

@Export
fun queryIntentActivityOptions(
    packageManager: PackageManager,
    caller: ComponentName?,
    specifics: Array<Intent?>?,
    intent: Intent,
    flags: Int,
): List<ResolveInfo>? {
    return visible(
        packageManager.queryIntentActivityOptions(caller, specifics, intent, flags),
        RESOLVE_INFO,
    )
}

@Export
fun resolveActivity(packageManager: PackageManager, intent: Intent, flags: Int): ResolveInfo? {
    return visible(packageManager.resolveActivity(intent, flags))
}

@Export
fun resolveService(packageManager: PackageManager, intent: Intent, flags: Int): ResolveInfo? {
    return visible(packageManager.resolveService(intent, flags))
}

@Export
fun resolveContentProvider(
    packageManager: PackageManager,
    authority: String,
    flags: Int,
): ProviderInfo? {
    return visible(packageManager.resolveContentProvider(authority, flags))
}

@Export
fun queryContentProviders(
    packageManager: PackageManager,
    processName: String?,
    packageUid: Int,
    flags: Int,
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
    flags: Int,
): ActivityInfo {
    requireVisible(component)
    return packageManager.getActivityInfo(component, flags)
}

@Export
fun getServiceInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: Int,
): ServiceInfo {
    requireVisible(component)
    return packageManager.getServiceInfo(component, flags)
}

@Export
fun getReceiverInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: Int,
): ActivityInfo {
    requireVisible(component)
    return packageManager.getReceiverInfo(component, flags)
}

@Export
fun getProviderInfo(
    packageManager: PackageManager,
    component: ComponentName,
    flags: Int,
): ProviderInfo {
    requireVisible(component)
    return packageManager.getProviderInfo(component, flags)
}

@Export
fun getComponentEnabledSetting(packageManager: PackageManager, component: ComponentName): Int {
    Objects.requireNonNull(component, "component")

    if (isHidden(component.packageName)) throw IllegalArgumentException("Unknown component: $component")

    return packageManager.getComponentEnabledSetting(component)
}
