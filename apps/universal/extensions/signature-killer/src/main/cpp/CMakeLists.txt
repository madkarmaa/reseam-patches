# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)

project(SignatureKiller C CXX ASM)

if (NOT ANDROID_ABI STREQUAL "arm64-v8a" AND NOT ANDROID_ABI STREQUAL "armeabi-v7a")
    message(FATAL_ERROR "SignatureKiller supports only armeabi-v7a and arm64-v8a")
endif ()

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

# Shared by the library and the standalone device tests.
set(NATIVE_HOOK_SOURCES
        hooks/native_file_hooks.cpp
        hooks/native_file_io.cpp
        hooks/native_file_metadata.cpp
        hooks/native_memory_hooks.cpp
        hooks/native_stdio.cpp
        hooks/native_libc_forwarders.cpp
        hooks/native_branch.cpp)

set(LIBRARY_VIEW_SOURCES
        view/library_view.cpp
        view/library_modules.cpp
        view/process_status.cpp
        view/process_snapshot.cpp
        view/snapshot_channel.cpp)

set(RAW_REDIRECT_SOURCES
        redirect/seccomp_open.cpp
        redirect/raw_redirect_identity.cpp
        redirect/raw_redirect_dispatch.cpp
        redirect/seccomp_filter.cpp
        redirect/raw_redirect_paths.cpp
        redirect/raw_redirect_metadata.cpp
        redirect/raw_redirect_content.cpp
        redirect/raw_redirect_open.cpp
        redirect/raw_redirect_signal.cpp
        redirect/raw_redirect_diagnostics.cpp)

add_library(SignatureKiller SHARED
        code_memory.cpp
        creator_view.cpp
        file_hooks.cpp
        jni.cpp
        library_file_redirect.cpp
        ${LIBRARY_VIEW_SOURCES}
        ${NATIVE_HOOK_SOURCES}
        ${RAW_REDIRECT_SOURCES}
        raw_syscall.S)

target_include_directories(SignatureKiller PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
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

# Keep ShadowHook private, including its linker-calibration ELF, in the one shipped library.
include(ShadowHook.cmake)
target_include_directories(SignatureKiller SYSTEM PRIVATE shadowhook/include)
target_link_libraries(SignatureKiller PRIVATE shadowhook)

# The patch module bundles the prebuilt libraries from its resources, one directory per ABI.
install(TARGETS SignatureKiller LIBRARY DESTINATION ${ANDROID_ABI})

option(SIGNATURE_KILLER_BUILD_TESTS "Build standalone native file-redirection tests" OFF)
if (SIGNATURE_KILLER_BUILD_TESTS)
    add_executable(signature_file_tests
            tests/files/file_redirect.cpp
            tests/files/file_platform.cpp
            tests/files/file_fixture.cpp
            tests/files/file_scenarios.cpp
            tests/files/file_metadata.cpp
            tests/files/file_contents.cpp
            ${RAW_REDIRECT_SOURCES}
            raw_syscall.S)
    target_include_directories(signature_file_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_compile_features(signature_file_tests PRIVATE cxx_std_23)
    target_compile_options(signature_file_tests PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)
    # Static bionic tests run directly on the matching Linux/Android CPU, without an emulator.
    target_link_options(signature_file_tests PRIVATE -static -Wl,--wrap=dl_iterate_phdr)
    target_link_libraries(signature_file_tests PRIVATE dl)

    add_library(signature_view_probe SHARED tests/library_probe.cpp)
    target_link_libraries(signature_view_probe PRIVATE dl)
    add_executable(signature_view_tests tests/library_view.cpp ${LIBRARY_VIEW_SOURCES} raw_syscall.S)
    target_include_directories(signature_view_tests PRIVATE . shadowhook/include)
    target_compile_features(signature_view_tests PRIVATE cxx_std_23)
    target_compile_options(signature_view_tests PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)
    target_link_options(signature_view_tests PRIVATE "LINKER:-rpath,$ORIGIN")
    target_link_libraries(signature_view_tests PRIVATE signature_view_probe shadowhook log dl)

    add_library(signature_unpacked_probe SHARED
            tests/unpacked_probe.cpp tests/unpacked_bridge.S raw_syscall.S)
    target_compile_features(signature_unpacked_probe PRIVATE cxx_std_23)
    target_link_options(signature_unpacked_probe PRIVATE -static-libstdc++)
    add_executable(signature_unpacked_tests
            tests/unpacked_redirect.cpp
            library_file_redirect.cpp
            ${NATIVE_HOOK_SOURCES}
            ${LIBRARY_VIEW_SOURCES}
            ${RAW_REDIRECT_SOURCES}
            raw_syscall.S)
    target_include_directories(signature_unpacked_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_include_directories(signature_unpacked_tests SYSTEM PRIVATE shadowhook/include)
    target_compile_features(signature_unpacked_tests PRIVATE cxx_std_23)
    target_compile_options(signature_unpacked_tests PRIVATE
            -Wall -Wextra -Werror -fno-exceptions -fno-rtti)
    target_link_options(signature_unpacked_tests PRIVATE "LINKER:-rpath,$ORIGIN")
    target_link_libraries(signature_unpacked_tests PRIVATE shadowhook log dl)
endif ()
