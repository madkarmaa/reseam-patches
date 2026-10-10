// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// settings.gradle.kts includes this module because the workspace plugin
// only discovers apps and extensions. Project dependencies compile these
// helpers into each consumer's patches JAR, without a separate bundle entry.

plugins {
    // The engine checkout supplies Kotlin Gradle plugin 2.4.10 on the
    // classpath. Specifying a version here causes resolution to fail.
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Must match the workspace plugin version in settings.gradle.kts.
    implementation("app.reseam:reseam-patch-sdk:0.20.1")
}
