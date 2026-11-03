// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

dependencies {
    compileOnly(project(":apps:universal:patch"))
}

// Rebuild the checked-in native library from extensions/accounts/src/main/cpp with ANDROID_NDK_HOME set:
// cmake --preset arm64-v8a && cmake --build --preset arm64-v8a --target install
