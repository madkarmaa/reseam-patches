dependencies {
    compileOnly("androidx.datastore:datastore-preferences-proto:1.2.1")
    compileOnly("androidx.datastore:datastore-preferences-external-protobuf:1.2.1")
}

// The extension DEX ships only this module's classes by default; the proto
// runtime the merge code links against must be dexed in explicitly. The app
// keeps no datastore classes under their real names, so nothing collides
val protoRuntime = configurations.detachedConfiguration(
    dependencies.create("androidx.datastore:datastore-preferences-proto:1.2.1") {
        isTransitive = false
    },
    dependencies.create("androidx.datastore:datastore-preferences-external-protobuf:1.2.1") {
        isTransitive = false
    },
)

tasks.named<app.reseam.gradle.DexTask>("dex") {
    sources.from(protoRuntime)
}
