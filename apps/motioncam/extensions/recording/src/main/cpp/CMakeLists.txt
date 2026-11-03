# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)
project(MotionCamRecording CXX ASM)

if (NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Unsupported MotionCamRecording ABI: ${ANDROID_ABI}")
endif ()

add_library(MotionCamRecording SHARED recording_limit.S recording_limit.cpp recording.cpp)
target_compile_features(MotionCamRecording PRIVATE cxx_std_23)
target_compile_options(MotionCamRecording PRIVATE
        "$<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Werror;-fno-exceptions;-fno-rtti;-fvisibility=hidden>")
target_link_options(MotionCamRecording PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-debug)
target_link_libraries(MotionCamRecording PRIVATE log dl)
install(TARGETS MotionCamRecording LIBRARY DESTINATION ${ANDROID_ABI})
