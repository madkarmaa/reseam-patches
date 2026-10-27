// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

dependencies {
    implementation(project(":apps:shared"))
}

// To rebuild the native libs into src/main/resources/lib, set ANDROID_NDK_HOME (NDK 30.0.16248370),
// put CMake 3.22+ and Ninja on PATH, and run this from
// apps/universal/extensions/signature-killer/src/main/cpp:
//
// for abi in armeabi-v7a arm64-v8a x86 x86_64; do
//     cmake --preset $abi && cmake --build --preset $abi --target install || break
// done
