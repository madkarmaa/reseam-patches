// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

dependencies {
    // The app keeps no datastore classes under their real names, so nothing collides.
    implementation("androidx.datastore:datastore-preferences-proto:1.2.1") {
        isTransitive = false
    }
    implementation("androidx.datastore:datastore-preferences-external-protobuf:1.2.1") {
        isTransitive = false
    }
}
