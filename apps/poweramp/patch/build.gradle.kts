// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

// Rebuild the native extension with ANDROID_NDK_HOME set:
// cd ../extensions/fullversion/src/main/cpp && cmake --preset arm64-v8a && cmake --build --preset
// arm64-v8a

dependencies {
    compileOnly(project(":apps:shared:patch"))
    compileOnly(project(":apps:universal:patch"))
}

reseamStrings { objectName = "top.madkarma.patches.poweramp.PowerampPatchStrings" }
