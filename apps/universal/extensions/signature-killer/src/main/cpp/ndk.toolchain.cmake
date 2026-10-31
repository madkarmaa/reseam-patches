# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

if (NOT ANDROID_NDK_HOME)
    set(ANDROID_NDK_HOME "$ENV{ANDROID_NDK_HOME}")
endif ()
if (NOT ANDROID_NDK_HOME)
    set(ANDROID_NDK_HOME "$ENV{ANDROID_NDK_ROOT}")
endif ()
if (NOT ANDROID_NDK_HOME)
    file(GLOB NDK_CANDIDATES LIST_DIRECTORIES true "$ENV{HOME}/Android/Sdk/ndk/*")
    if (NDK_CANDIDATES)
        list(SORT NDK_CANDIDATES COMPARE NATURAL ORDER DESCENDING)
        list(GET NDK_CANDIDATES 0 ANDROID_NDK_HOME)
    endif ()
endif ()

if (NOT EXISTS "${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake")
    message(FATAL_ERROR "Android NDK not found: set ANDROID_NDK_HOME to an NDK installation.")
endif ()

include("${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake")
