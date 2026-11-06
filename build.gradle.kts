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

// Ship the license texts and notices inside the signed bundle.
tasks.named<Sync>("stageBundle") {
    from(layout.projectDirectory.file("LICENSE")) { into("resources/legal") }
    from(layout.projectDirectory.file("NOTICE")) { into("resources/legal") }
    from(layout.projectDirectory.file("REUSE.toml")) { into("resources/legal") }
    from(layout.projectDirectory.dir("LICENSES")) { into("resources/legal/LICENSES") }

    val nativeSources = "apps/universal/extensions/signature-killer/src/main/cpp"
    from(layout.projectDirectory.dir(nativeSources)) {
        include(
            "bytehook/**/LICENSE",
            "shadowhook/**/LICENSE",
            "shadowhook/VERSION",
            "llvm-runtime.NOTICE",
        )
        into("resources/legal/signature-killer")
    }
}
