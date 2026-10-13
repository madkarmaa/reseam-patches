// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.secp192k1.universal.hidepackages;

import static dev.secp192k1.universal.hidepackages.HiddenPackages.*;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.*;
import android.content.pm.PackageManager.*;

import java.util.List;

@SuppressWarnings("unused")
public final class HiddenPackagesApi33 {
    private HiddenPackagesApi33() {
    }

    public static PackageInfo getPackageInfo(PackageManager packageManager, String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageInfo(packageName, flags);
    }

    public static PackageInfo getPackageInfo(PackageManager packageManager, VersionedPackage versionedPackage, PackageInfoFlags flags) throws NameNotFoundException {
        requireVisible(versionedPackage.getPackageName());
        return packageManager.getPackageInfo(versionedPackage, flags);
    }

    public static ApplicationInfo getApplicationInfo(PackageManager packageManager, String packageName, ApplicationInfoFlags flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getApplicationInfo(packageName, flags);
    }

    public static int getPackageUid(PackageManager packageManager, String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageUid(packageName, flags);
    }

    public static int[] getPackageGids(PackageManager packageManager, String packageName, PackageInfoFlags flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageGids(packageName, flags);
    }

    public static List<PackageInfo> getInstalledPackages(PackageManager packageManager, PackageInfoFlags flags) {
        return visible(packageManager.getInstalledPackages(flags), PACKAGE_INFO);
    }

    public static List<ApplicationInfo> getInstalledApplications(PackageManager packageManager, ApplicationInfoFlags flags) {
        return visible(packageManager.getInstalledApplications(flags), APPLICATION_INFO);
    }

    public static List<PackageInfo> getPackagesHoldingPermissions(PackageManager packageManager, String[] permissions, PackageInfoFlags flags) {
        return visible(packageManager.getPackagesHoldingPermissions(permissions, flags), PACKAGE_INFO);
    }

    public static List<ResolveInfo> queryIntentActivities(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.queryIntentActivities(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentServices(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.queryIntentServices(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryBroadcastReceivers(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.queryBroadcastReceivers(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentContentProviders(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.queryIntentContentProviders(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentActivityOptions(PackageManager packageManager, ComponentName caller, List<Intent> specifics, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.queryIntentActivityOptions(caller, specifics, intent, flags), RESOLVE_INFO);
    }

    public static ResolveInfo resolveActivity(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.resolveActivity(intent, flags));
    }

    public static ResolveInfo resolveService(PackageManager packageManager, Intent intent, ResolveInfoFlags flags) {
        return visible(packageManager.resolveService(intent, flags));
    }

    public static ProviderInfo resolveContentProvider(PackageManager packageManager, String authority, ComponentInfoFlags flags) {
        return visible(packageManager.resolveContentProvider(authority, flags));
    }

    public static List<ProviderInfo> queryContentProviders(PackageManager packageManager, String processName, int packageUid, ComponentInfoFlags flags) {
        return visible(packageManager.queryContentProviders(processName, packageUid, flags), PROVIDER_INFO);
    }

    public static ActivityInfo getActivityInfo(PackageManager packageManager, ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getActivityInfo(component, flags);
    }

    public static ServiceInfo getServiceInfo(PackageManager packageManager, ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getServiceInfo(component, flags);
    }

    public static ActivityInfo getReceiverInfo(PackageManager packageManager, ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getReceiverInfo(component, flags);
    }

    public static ProviderInfo getProviderInfo(PackageManager packageManager, ComponentName component, ComponentInfoFlags flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getProviderInfo(component, flags);
    }

    public static boolean canPackageQuery(PackageManager packageManager, String sourcePackageName, String targetPackageName) throws NameNotFoundException {
        requireVisible(sourcePackageName);
        requireVisible(targetPackageName);
        return packageManager.canPackageQuery(sourcePackageName, targetPackageName);
    }
}
