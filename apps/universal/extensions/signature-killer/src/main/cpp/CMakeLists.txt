# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)

project(SignatureKiller C CXX ASM)

# Export only the JNI entry points. Setting the presets keeps the NDK's default compiler flags.
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

# Vendored bytehook 1.0.10 without its Java glue. Linking it statically keeps it separate
# from any bytehook copy the host app ships.
file(GLOB BYTEHOOK_SOURCES bytehook/*.c)
add_library(bytehook STATIC ${BYTEHOOK_SOURCES})
target_compile_features(bytehook PRIVATE c_std_17)
target_compile_options(bytehook PRIVATE -Oz -ffunction-sections -fdata-sections)
# As system headers, bytehook's macros keep their warnings out of this library's diagnostics.
target_include_directories(bytehook SYSTEM PUBLIC
    bytehook
    bytehook/include
    bytehook/third_party/bsd
    bytehook/third_party/lss)
target_include_directories(bytehook PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

add_library(SignatureKiller SHARED
    code_memory.cpp
    file_hooks.cpp
    jni.cpp
    library_file_redirect.cpp
    seccomp_open.cpp
    raw_syscall.S)

target_compile_features(SignatureKiller PRIVATE cxx_std_23)
# Nothing may unwind through hooked libc calls or across JNI, and nothing needs type information.
target_compile_options(SignatureKiller PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)

# Keep the static bytehook and libc++ symbols private so they never interpose on the host's copies,
# and drop the debug info the static libc++ archive carries.
target_link_options(SignatureKiller PRIVATE
    -Wl,--exclude-libs,ALL
    -Wl,--gc-sections
    -Wl,--strip-debug)

target_link_libraries(SignatureKiller PRIVATE bytehook log dl)

# The patch module bundles the prebuilt libraries from its resources, one directory per ABI.
install(TARGETS SignatureKiller LIBRARY DESTINATION ${ANDROID_ABI})

option(SIGNATURE_KILLER_BUILD_TESTS "Build standalone native file-redirection tests" OFF)
if (SIGNATURE_KILLER_BUILD_TESTS)
    add_executable(signature_file_tests tests/file_redirect.cpp seccomp_open.cpp raw_syscall.S)
    target_include_directories(signature_file_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_compile_features(signature_file_tests PRIVATE cxx_std_23)
    target_compile_options(signature_file_tests PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)
    # Static bionic tests run directly on the matching Linux/Android CPU, without an emulator.
    target_link_options(signature_file_tests PRIVATE -static -Wl,--wrap=dl_iterate_phdr)
    target_link_libraries(signature_file_tests PRIVATE dl)
endif ()
