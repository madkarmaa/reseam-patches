// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.secp192k1.universal.hidepackages;

import static dev.secp192k1.universal.hidepackages.HiddenPackages.requireVisible;

import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

@SuppressWarnings("unused")
public final class HiddenPackagesApi31 {
    private HiddenPackagesApi31() {
    }

    public static int getTargetSdkVersion(PackageManager packageManager, String packageName) throws NameNotFoundException {
        requireVisible(packageName);
        return packageManager.getTargetSdkVersion(packageName);
    }
}
