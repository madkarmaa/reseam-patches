// Shared Kotlin helpers for patch modules (NOT an app, NOT an extension:
// the workspace plugin ignores this directory, so it is wired manually
// below in settings.gradle.kts). Compiled into each consumer's patches jar
// through normal project dependencies; nothing is staged into the bundle.

plugins {
    // Version omitted on purpose: the Kotlin plugin is already on the
    // classpath through the engine checkout, and requesting a version
    // fails resolution. Matches the engine's KGP (2.4.10).
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Must match the workspace plugin version in settings.gradle.kts.
    implementation("app.reseam:reseam-patch-sdk:0.12.1")
}
