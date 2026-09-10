package top.madkarma.patches.universal

import app.reseam.patch.patch

val removePairip =
    patch("Remove Pairip") {
        description("Remove Pairip tamper protection")

        execute {
            manifest.edit {
                findByAttribute("android:name", "com.google.android.play.core.common.PlayCoreDialogWrapperActivity").forEach { it.remove() }
                findByAttribute("android:name", "com.pairip.licensecheck.LicenseActivity").forEach { it.remove() }
                findByAttribute("android:name", "com.android.vending.CHECK_LICENSE").forEach { it.remove() }
            }
        }
    }
