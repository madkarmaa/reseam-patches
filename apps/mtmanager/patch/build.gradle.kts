// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

dependencies {
    compileOnly(project(":apps:universal:patch"))
}

reseamStrings {
    objectName = "top.madkarma.patches.mtmanager.MTManagerPatchStrings"
}

// Rebuild from extensions/vip/src/main/cpp with ANDROID_NDK_HOME set and CMake/Ninja on PATH:
// for abi in armeabi-v7a arm64-v8a x86 x86_64; do
//     cmake --preset $abi && cmake --build --preset $abi || break
// done
