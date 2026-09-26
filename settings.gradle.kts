// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: GPL-3.0-or-later

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://git.reseam.app/api/packages/reseam/maven") {
            mavenContent { includeGroupAndSubgroups("app.reseam") }
        }
    }

    includeBuild("revisions-gradle-plugin")

    (System.getenv("RESEAM_WORKSPACE") ?: providers.gradleProperty("reseam.workspace").orNull)
        ?.takeIf { it.isNotBlank() }
        ?.let { includeBuild(it) }
}

plugins {
    id("app.reseam.workspace") version "0.12.1"
}

rootProject.name = "madkarma-patches"

// Shared patch helpers. Not an app or extension, so the workspace plugin
// above ignores it; wired manually like any plain Gradle module.
include("apps:shared")

dependencyResolutionManagement {
    repositories {
        google()
    }
}
