// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: AGPL-3.0-or-later
// Reseam-derived material: see NOTICE for applicable section 7 terms.

package top.madkarma.patches.googlephotos.gms

import app.reseam.patch.*
import top.madkarma.patches.googlephotos.GOOGLE_PHOTOS
import top.madkarma.patches.shared.originalSignerSha1

private const val MARS_AUTHORITY = "com.google.android.libraries.photos.api.mars"

private val PERMISSION_DECLARATIONS =
    setOf("permission", "uses-permission", "uses-permission-sdk-23")

private val PERMISSION_ATTRIBUTES =
    listOf("android:permission", "android:readPermission", "android:writePermission")

internal fun PatchRuntime.renameGmsCorePackages(
    vendor: String,
    newPackage: String,
): Pair<Int, Int> {
    val gmsCorePackage = "$vendor.android.gms"
    val marsAuthority = "$newPackage.api.mars"
    val appNames = mutableSetOf<String>()

    fun renameAppName(value: String): String? =
        when {
            value == MARS_AUTHORITY -> marsAuthority
            value.startsWith("$GOOGLE_PHOTOS.") -> newPackage + value.removePrefix(GOOGLE_PHOTOS)
            value.startsWith("org.microg.gms.") -> "$newPackage.$value"
            else -> null
        }

    fun rename(value: String): String? =
        when (value) {
            in GMS_CONSTANTS ->
                when (value) {
                    "subscribedfeeds" -> "$vendor.android.gsf.subscribedfeeds"
                    else -> vendor + value.removePrefix("com.google")
                }

            in appNames,
            MARS_AUTHORITY ->
                renameAppName(
                    value,
                )

            else -> null
        }

    for (component in manifest.components()) {
        manifest.component(component).edit {
            // Relative class names must still resolve to the original DEX classes after
            // renaming.
            for (element in root.descendants()) {
                if (
                    element.tag in
                        setOf(
                            "application",
                            "activity",
                            "activity-alias",
                            "service",
                            "receiver",
                            "provider",
                        )
                ) {
                    for (attribute in
                        listOf(
                            "android:name",
                            "android:targetActivity",
                            "android:backupAgent",
                            "android:manageSpaceActivity",
                        )) {
                        val value = element[attribute] ?: continue
                        if (value.startsWith(".")) element[attribute] = GOOGLE_PHOTOS + value
                        else if ('.' !in value) element[attribute] = "$GOOGLE_PHOTOS.$value"
                    }
                }

                if (element.tag in PERMISSION_DECLARATIONS) {
                    val value = element["android:name"]
                    if (value != null) {
                        renameAppName(value)?.let { appNames += value }
                        rename(value)?.let { element["android:name"] = it }
                    }
                }

                for (attribute in PERMISSION_ATTRIBUTES) {
                    val value = element[attribute] ?: continue
                    renameAppName(value)?.let { appNames += value }
                    rename(value)?.let { element[attribute] = it }
                }

                element["android:authorities"]?.let { authorities ->
                    element["android:authorities"] =
                        authorities.split(';').joinToString(";") { authority ->
                            renameAppName(authority)?.let { appNames += authority }
                            rename(authority) ?: authority
                        }
                }

                if (element.tag in setOf("action", "package")) {
                    val value = element["android:name"] ?: continue
                    rename(value)?.let { element["android:name"] = it }
                }

                if (element["android:host"] == MARS_AUTHORITY)
                    element["android:host"] = marsAuthority

                element["android:requiredAccountType"]?.let { value ->
                    rename(value)?.let { element["android:requiredAccountType"] = it }
                }
            }
            root["package"] = newPackage
        }
    }

    resources.components().forEach { resources.component(it).setPackageName(newPackage) }

    manifest.addPermission("org.microg.gms.permission.FAKE_PACKAGE_SIGNATURE")
    manifest.addPermission("$vendor.org.microg.gms.EXTENDED_ACCESS")

    // GmsCore authenticates as Photos' original Google signer, never the workspace's APK
    // signer.
    val originalSignature = originalSignerSha1()
    log.info("GmsCore: presents the original signing certificate $originalSignature.")

    manifest.edit {
        val queries =
            findByTag("queries").firstOrNull()
                ?: createElement("queries").also { root.appendChild(it) }

        if (
            queries.children.none {
                it.tag == "package" && it["android:name"] == gmsCorePackage
            }
        )
            queries.appendChild(
                createElement("package").apply {
                    this["android:name"] = gmsCorePackage
                },
            )

        val application =
            findByTag("application").singleOrNull() ?: error("Photos Application is missing")

        for ((name, value) in
            mapOf(
                "$gmsCorePackage.SPOOFED_PACKAGE_NAME" to GOOGLE_PHOTOS,
                "$gmsCorePackage.SPOOFED_PACKAGE_SIGNATURE" to originalSignature,
                "$vendor.MICROG_PACKAGE_NAME" to gmsCorePackage,
            )) application.appendChild(
            createElement("meta-data").apply {
                this["android:name"] = name
                this["android:value"] = value
            },
        )
    }

    // Rewrite only known protocol strings and declared app authorities/permissions,
    // preserving class names.
    val constants =
        (GMS_CONSTANTS + appNames + MARS_AUTHORITY).sumOf { old ->
            rename(old)?.let { bytecode.replaceAllStrings(old, it) } ?: 0
        }

    check(constants > 0) { "No Google Photos GmsCore constants matched" }

    val uris =
        bytecode.replaceStringsContaining("content://") { uri ->
            if (!uri.startsWith("content://")) return@replaceStringsContaining null
            val rest = uri.removePrefix("content://")
            val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }

            rename(authority)?.let { "content://$it${rest.removePrefix(authority)}" }
        }

    return constants to uris
}

private fun XmlElement.descendants(): Sequence<XmlElement> =
    sequenceOf(this) + children.asSequence().flatMap { it.descendants() }
