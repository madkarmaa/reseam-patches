// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

// Rebuild the native extension with ANDROID_NDK_HOME set:
// cd ../extensions/recording/src/main/cpp && cmake --preset arm64-v8a && cmake --build --preset arm64-v8a
