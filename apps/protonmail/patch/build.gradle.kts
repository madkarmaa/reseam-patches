// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

dependencies {
    compileOnly(project(":apps:universal:patch"))
}

// Rebuild the checked-in native libraries from extensions/accounts/src/main/cpp with
 // ANDROID_NDK_HOME set.
// For each ABI (arm64-v8a, armeabi-v7a, x86_64): cmake --preset <abi> && cmake --build --preset
// <abi>
