// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("com.diffplug.spotless") version "8.11.0"
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")

        googleJavaFormat().aosp()

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlin {
        target("**/*.kt")
        // Eclipse-based tooling such as jdtls copies the generated proto sources into bin.
        targetExclude("**/build/**", "**/bin/**")

        ktfmt().configure {
            it.setMaxWidth(100)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }

        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")

        ktfmt().configure {
            it.setMaxWidth(100)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
        }

        trimTrailingWhitespace()
        endWithNewline()
    }
}

val bun: String =
    providers
        .environmentVariable("PATH")
        .get()
        .split(File.pathSeparator)
        .map { File(it, "bun") }
        .firstOrNull { it.canExecute() }
        ?.path ?: "${System.getProperty("user.home")}/.bun/bin/bun"

val formatWithBun = tasks.register<Exec>("formatWithBun") { commandLine(bun, "fmt") }

tasks.spotlessApply { finalizedBy(formatWithBun) }

val checkFormatWithBun =
    tasks.register<Exec>("checkFormatWithBun") { commandLine(bun, "fmt", "--check") }

tasks.spotlessCheck { finalizedBy(checkFormatWithBun) }

// The workspace plugin includes resources/ in the signed bundle's module fingerprints.
val bundleLegalResources =
    tasks.register<Sync>("bundleLegalResources") {
        into(layout.projectDirectory.dir("resources/legal"))
        from(layout.projectDirectory.file("LICENSE"))
        from(layout.projectDirectory.file("NOTICE"))
        from(layout.projectDirectory.file("REUSE.toml"))
        from(layout.projectDirectory.dir("LICENSES")) { into("LICENSES") }

        val nativeSources = "apps/universal/extensions/signature-killer/src/main/cpp"
        from(layout.projectDirectory.dir(nativeSources)) {
            include(
                "bytehook/**/LICENSE",
                "shadowhook/**/LICENSE",
                "shadowhook/VERSION",
                "llvm-runtime.NOTICE",
            )
            into("signature-killer")
        }
    }

tasks.named("stageBundle") { dependsOn(bundleLegalResources) }

tasks.named<Delete>("clean") { delete(bundleLegalResources.map { it.destinationDir }) }
