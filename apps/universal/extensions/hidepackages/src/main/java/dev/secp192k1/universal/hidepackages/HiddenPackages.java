// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.secp192k1.universal.hidepackages;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.util.*;

// Keep newer Android API calls in separate classes so older devices can verify this class.
@SuppressWarnings("unused")
public final class HiddenPackages {
    static final PackageNameReader<PackageInfo> PACKAGE_INFO = packageInfo -> packageInfo.packageName;
    static final PackageNameReader<ApplicationInfo> APPLICATION_INFO = applicationInfo -> applicationInfo.packageName;
    static final PackageNameReader<ProviderInfo> PROVIDER_INFO = providerInfo -> providerInfo.packageName;
    static final PackageNameReader<ResolveInfo> RESOLVE_INFO = HiddenPackages::packageNameOf;
    private static volatile Set<String> hiddenPackageNames = Collections.emptySet();

    private HiddenPackages() {
    }

    public static void initialize(String joinedPackageNames) {
        if (!hiddenPackageNames.isEmpty()) return;

        // Publish a complete set. Concurrent first calls may build the same set independently.
        hiddenPackageNames = new HashSet<>(Arrays.asList(joinedPackageNames.split(",")));
    }

    static boolean isHidden(String packageName) {
        return packageName != null && hiddenPackageNames.contains(packageName);
    }

    static boolean isEveryPackageHidden(PackageManager packageManager, int packageUid) {
        String[] packageNames = packageManager.getPackagesForUid(packageUid);
        if (packageNames == null || packageNames.length == 0) return false;

        for (String packageName : packageNames) {
            if (!isHidden(packageName)) return false;
        }

        return true;
    }

    static void requireVisible(String packageName) throws NameNotFoundException {
        if (isHidden(packageName)) throw new NameNotFoundException(packageName);
    }

    static void requireVisible(ComponentName component) throws NameNotFoundException {
        if (component != null && isHidden(component.getPackageName()))
            throw new NameNotFoundException(component.toString());
    }

    static void requireKnownPackage(String packageName) {
        if (isHidden(packageName))
            throw new IllegalArgumentException("Unknown package: " + packageName);
    }

    static String packageNameOf(ResolveInfo resolveInfo) {
        if (resolveInfo.activityInfo != null) return resolveInfo.activityInfo.packageName;
        if (resolveInfo.serviceInfo != null) return resolveInfo.serviceInfo.packageName;
        if (resolveInfo.providerInfo != null) return resolveInfo.providerInfo.packageName;
        return resolveInfo.resolvePackageName;
    }

    static ResolveInfo visible(ResolveInfo resolveInfo) {
        if (resolveInfo != null && isHidden(packageNameOf(resolveInfo))) return null;
        return resolveInfo;
    }

    static ProviderInfo visible(ProviderInfo providerInfo) {
        if (providerInfo != null && isHidden(providerInfo.packageName)) return null;
        return providerInfo;
    }

    // Return the original list unless a hidden package needs to be removed.
    static <T> List<T> visible(List<T> items, PackageNameReader<T> reader) {
        if (items == null) return null;

        int size = items.size();
        int firstHiddenIndex = 0;

        while (firstHiddenIndex < size && !isHidden(reader.packageNameOf(items.get(firstHiddenIndex))))
            firstHiddenIndex++;

        if (firstHiddenIndex == size) return items;

        ArrayList<T> visibleItems = new ArrayList<>(size - 1);
        visibleItems.addAll(items.subList(0, firstHiddenIndex));

        for (int index = firstHiddenIndex + 1; index < size; index++) {
            T item = items.get(index);
            if (!isHidden(reader.packageNameOf(item))) visibleItems.add(item);
        }

        return visibleItems;
    }

    public static PackageInfo getPackageInfo(PackageManager packageManager, String packageName, int flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageInfo(packageName, flags);
    }

    public static PackageInfo getPackageInfo(PackageManager packageManager, VersionedPackage versionedPackage, int flags) throws NameNotFoundException {
        requireVisible(versionedPackage.getPackageName());
        return packageManager.getPackageInfo(versionedPackage, flags);
    }

    public static ApplicationInfo getApplicationInfo(PackageManager packageManager, String packageName, int flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getApplicationInfo(packageName, flags);
    }

    public static int getPackageUid(PackageManager packageManager, String packageName, int flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageUid(packageName, flags);
    }

    public static int[] getPackageGids(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageGids(packageName);
    }

    public static int[] getPackageGids(PackageManager packageManager, String packageName, int flags) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getPackageGids(packageName, flags);
    }

    public static Intent getLaunchIntentForPackage(PackageManager packageManager, String packageName) {
        if (isHidden(packageName)) return null;
        return packageManager.getLaunchIntentForPackage(packageName);
    }

    public static Intent getLeanbackLaunchIntentForPackage(PackageManager packageManager, String packageName) {
        if (isHidden(packageName)) return null;
        return packageManager.getLeanbackLaunchIntentForPackage(packageName);
    }

    public static Drawable getApplicationIcon(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getApplicationIcon(packageName);
    }

    public static Drawable getApplicationBanner(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getApplicationBanner(packageName);
    }

    public static Drawable getApplicationLogo(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getApplicationLogo(packageName);
    }

    public static Resources getResourcesForApplication(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getResourcesForApplication(packageName);
    }

    public static Drawable getDrawable(PackageManager packageManager, String packageName, int resourceId, ApplicationInfo applicationInfo) {
        if (isHidden(packageName)) return null;
        return packageManager.getDrawable(packageName, resourceId, applicationInfo);
    }

    public static CharSequence getText(PackageManager packageManager, String packageName, int resourceId, ApplicationInfo applicationInfo) {
        if (isHidden(packageName)) return null;
        return packageManager.getText(packageName, resourceId, applicationInfo);
    }

    public static int getApplicationEnabledSetting(PackageManager packageManager, String packageName) {
        requireKnownPackage(packageName);
        return packageManager.getApplicationEnabledSetting(packageName);
    }

    @SuppressWarnings("deprecation")
    public static String getInstallerPackageName(PackageManager packageManager, String packageName) {
        requireKnownPackage(packageName);
        return packageManager.getInstallerPackageName(packageName);
    }

    public static int checkSignatures(PackageManager packageManager, String firstPackageName, String secondPackageName) {
        if (isHidden(firstPackageName) || isHidden(secondPackageName))
            return PackageManager.SIGNATURE_UNKNOWN_PACKAGE;
        return packageManager.checkSignatures(firstPackageName, secondPackageName);
    }

    public static int checkPermission(PackageManager packageManager, String permissionName, String packageName) {
        if (isHidden(packageName)) return PackageManager.PERMISSION_DENIED;
        return packageManager.checkPermission(permissionName, packageName);
    }

    public static String[] getPackagesForUid(PackageManager packageManager, int packageUid) {
        String[] packageNames = packageManager.getPackagesForUid(packageUid);
        if (packageNames == null) return null;

        int visibleCount = 0;
        for (String packageName : packageNames) {
            if (!isHidden(packageName)) visibleCount++;
        }

        if (visibleCount == packageNames.length) return packageNames;

        if (visibleCount == 0) return null;

        String[] visiblePackageNames = new String[visibleCount];

        int index = 0;
        for (String packageName : packageNames) {
            if (!isHidden(packageName)) visiblePackageNames[index++] = packageName;
        }

        return visiblePackageNames;
    }

    public static String getNameForUid(PackageManager packageManager, int packageUid) {
        String name = packageManager.getNameForUid(packageUid);
        if (name == null || isHidden(name)) return null;

        // Hide a shared user ID only when all its packages are hidden.
        if (name.indexOf(':') >= 0 && isEveryPackageHidden(packageManager, packageUid)) return null;

        return name;
    }

    public static List<PackageInfo> getInstalledPackages(PackageManager packageManager, int flags) {
        return visible(packageManager.getInstalledPackages(flags), PACKAGE_INFO);
    }

    public static List<ApplicationInfo> getInstalledApplications(PackageManager packageManager, int flags) {
        return visible(packageManager.getInstalledApplications(flags), APPLICATION_INFO);
    }

    public static List<PackageInfo> getPackagesHoldingPermissions(PackageManager packageManager, String[] permissions, int flags) {
        return visible(packageManager.getPackagesHoldingPermissions(permissions, flags), PACKAGE_INFO);
    }

    public static List<ResolveInfo> queryIntentActivities(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.queryIntentActivities(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentServices(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.queryIntentServices(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryBroadcastReceivers(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.queryBroadcastReceivers(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentContentProviders(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.queryIntentContentProviders(intent, flags), RESOLVE_INFO);
    }

    public static List<ResolveInfo> queryIntentActivityOptions(PackageManager packageManager, ComponentName caller, Intent[] specifics, Intent intent, int flags) {
        return visible(packageManager.queryIntentActivityOptions(caller, specifics, intent, flags), RESOLVE_INFO);
    }

    public static ResolveInfo resolveActivity(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.resolveActivity(intent, flags));
    }

    public static ResolveInfo resolveService(PackageManager packageManager, Intent intent, int flags) {
        return visible(packageManager.resolveService(intent, flags));
    }

    public static ProviderInfo resolveContentProvider(PackageManager packageManager, String authority, int flags) {
        return visible(packageManager.resolveContentProvider(authority, flags));
    }

    public static List<ProviderInfo> queryContentProviders(PackageManager packageManager, String processName, int packageUid, int flags) {
        return visible(packageManager.queryContentProviders(processName, packageUid, flags), PROVIDER_INFO);
    }

    public static ActivityInfo getActivityInfo(PackageManager packageManager, ComponentName component, int flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getActivityInfo(component, flags);
    }

    public static ServiceInfo getServiceInfo(PackageManager packageManager, ComponentName component, int flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getServiceInfo(component, flags);
    }

    public static ActivityInfo getReceiverInfo(PackageManager packageManager, ComponentName component, int flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getReceiverInfo(component, flags);
    }

    public static ProviderInfo getProviderInfo(PackageManager packageManager, ComponentName component, int flags) throws NameNotFoundException {
        requireVisible(component);
        return packageManager.getProviderInfo(component, flags);
    }

    public static int getComponentEnabledSetting(PackageManager packageManager, ComponentName component) {
        Objects.requireNonNull(component, "component");

        if (isHidden(component.getPackageName()))
            throw new IllegalArgumentException("Unknown component: " + component);

        return packageManager.getComponentEnabledSetting(component);
    }

    interface PackageNameReader<T> {
        String packageNameOf(T item);
    }
}
