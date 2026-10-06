// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.googlephotos.spoof;

import android.os.SystemProperties;

@SuppressWarnings("unused")
public final class PixelXlIdentity {
    private static final String FINGERPRINT = "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys";

    private PixelXlIdentity() {
    }

    public static String get(String key) {
        String value = property(key);
        return value != null ? value : SystemProperties.get(key);
    }

    public static String get(String key, String fallback) {
        String value = property(key);
        return value != null ? value : SystemProperties.get(key, fallback);
    }

    private static String property(String key) {
        switch (key) {
            case "ro.build.id" -> {
                return "QP1A.191005.007.A3";
            }
            case "ro.build.version.incremental" -> {
                return "5972272";
            }
            case "ro.build.product" -> {
                return "marlin";
            }
            case "ro.product.model.marketname" -> {
                return "Pixel XL";
            }
            case "ro.build.fingerprint", "ro.vendor.build.fingerprint",
                 "ro.system.build.fingerprint", "ro.bootimage.build.fingerprint",
                 "ro.odm.build.fingerprint" -> {
                return FINGERPRINT;
            }
        }

        String product = key;
        for (String partition : new String[]{"system", "vendor", "odm"})
            if (product.startsWith("ro.product." + partition + "."))
                product = "ro.product." + product.substring(("ro.product." + partition + ".").length());

        return switch (product) {
            case "ro.product.brand" -> "google";
            case "ro.product.manufacturer" -> "Google";
            case "ro.product.device", "ro.product.name" -> "marlin";
            case "ro.product.model" -> "Pixel XL";
            default -> null;
        };
    }
}
