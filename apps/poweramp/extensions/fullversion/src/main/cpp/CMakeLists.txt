# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)
project(PowerampPlayback CXX ASM)

if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(LICENSE_CHECK_PATTERN license_check_arm64.S)
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    set(LICENSE_CHECK_PATTERN license_check_arm.S)
else ()
    message(FATAL_ERROR "Unsupported PowerampPlayback ABI: ${ANDROID_ABI}")
endif ()

add_library(PowerampPlayback SHARED ${LICENSE_CHECK_PATTERN} license_check.cpp playback.cpp)
target_compile_features(PowerampPlayback PRIVATE cxx_std_23)
target_compile_options(PowerampPlayback PRIVATE
        "$<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Werror;-fno-exceptions;-fno-rtti;-fvisibility=hidden>")
target_link_options(PowerampPlayback PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-debug)
target_link_libraries(PowerampPlayback PRIVATE log)
install(TARGETS PowerampPlayback LIBRARY DESTINATION ${ANDROID_ABI})
