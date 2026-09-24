// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.sam.with.receiver") version "2.4.10"
    `java-gradle-plugin`
}

samWithReceiver { annotation("org.gradle.api.HasImplicitReceiver") }

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("patchRevisions") {
            id = "madkarma.patch-revisions"
            implementationClass = "top.madkarma.gradle.revisions.PatchRevisionsPlugin"
        }
    }
}
