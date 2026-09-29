dependencies {
    // The app keeps no datastore classes under their real names, so nothing collides.
    implementation("androidx.datastore:datastore-preferences-proto:1.2.1") {
        isTransitive = false
    }
    implementation("androidx.datastore:datastore-preferences-external-protobuf:1.2.1") {
        isTransitive = false
    }
}
