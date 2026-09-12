// HiddenApiBypass ships as an AAR, which a plain java-library cannot put on
// the compile classpath. Fetch the AAR artifact explicitly, unzip its
// classes.jar, and use that for both compilation and dexing.
val hiddenAar = configurations.detachedConfiguration(
    dependencies.create("org.lsposed.hiddenapibypass:hiddenapibypass:6.1@aar") {
        isTransitive = false
    },
)

val hiddenClassesJar = tasks.register<Copy>("hiddenClassesJar") {
    description = "Extract lib's classes.jar from AAR"

    from({ hiddenAar.files.map { zipTree(it) } })
    include("classes.jar")
    into(layout.buildDirectory.dir("hiddenapibypass"))
    rename("classes\\.jar", "hiddenapibypass-classes.jar")
}

// The Copy task's outputs resolve to its destination directory, which is
// wrong for both javac and d8 (they need the jar file itself), so wire the
// renamed jar explicitly. The provider carries the producing task.
val hiddenJar =
    hiddenClassesJar.map { it.destinationDir.resolve("hiddenapibypass-classes.jar") }

dependencies {
    compileOnly(files(hiddenJar))
}

// The extension DEX ships only this module's classes by default; the
// HiddenApiBypass runtime the Java code links against must be dexed in
// explicitly.
tasks.named<app.reseam.gradle.DexTask>("dex") {
    sources.from(files(hiddenJar))
}
