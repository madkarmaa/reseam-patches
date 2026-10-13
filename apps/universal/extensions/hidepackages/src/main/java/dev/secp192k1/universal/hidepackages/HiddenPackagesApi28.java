// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.secp192k1.universal.hidepackages;

import static dev.secp192k1.universal.hidepackages.HiddenPackages.isEveryPackageHidden;
import static dev.secp192k1.universal.hidepackages.HiddenPackages.isHidden;

import android.content.pm.PackageManager;

@SuppressWarnings("unused")
public final class HiddenPackagesApi28 {
    private HiddenPackagesApi28() {
    }

    public static boolean hasSigningCertificate(PackageManager packageManager, String packageName, byte[] certificate, int type) {
        return !isHidden(packageName) && packageManager.hasSigningCertificate(packageName, certificate, type);
    }

    public static boolean hasSigningCertificate(PackageManager packageManager, int packageUid, byte[] certificate, int type) {
        return !isEveryPackageHidden(packageManager, packageUid) && packageManager.hasSigningCertificate(packageUid, certificate, type);
    }
}
