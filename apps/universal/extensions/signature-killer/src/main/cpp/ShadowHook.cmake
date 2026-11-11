# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(SHADOWHOOK_ARCH arm64)
else ()
    set(SHADOWHOOK_ARCH arm)
endif ()

file(GLOB SHADOWHOOK_SOURCES CONFIGURE_DEPENDS
        shadowhook/*.c
        shadowhook/arch/${SHADOWHOOK_ARCH}/*.c
        shadowhook/arch/${SHADOWHOOK_ARCH}/*.S
        shadowhook/common/*.c
        shadowhook/third_party/*/*.c)
# The extension owns JNI_OnLoad and uses the native API, without ShadowHook's Java bridge.
list(FILTER SHADOWHOOK_SOURCES EXCLUDE REGEX "/sh_jni\\.c$")
add_library(shadowhook STATIC ${SHADOWHOOK_SOURCES} shadowhook_calibration.cpp)
set_target_properties(shadowhook PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_features(shadowhook PRIVATE c_std_11 cxx_std_23)
target_compile_options(shadowhook PRIVATE -Os -ffunction-sections -fdata-sections)
target_include_directories(shadowhook SYSTEM PRIVATE
        shadowhook shadowhook/include shadowhook/arch/${SHADOWHOOK_ARCH}
        shadowhook/common shadowhook/third_party/xdl shadowhook/third_party/bsd
        shadowhook/third_party/lss)
target_link_libraries(shadowhook PUBLIC log dl)

# Adapt only the linker's calibration loading and name lookups; keep vendored source unchanged.
set_source_files_properties(shadowhook/sh_linker.c PROPERTIES
        COMPILE_DEFINITIONS
        "dlopen=signature_shadowhook_dlopen;xdl_open=signature_shadowhook_xdl_open;sh_util_ends_with=signature_shadowhook_ends_with")

# Build upstream's empty calibration ELF, then embed its bytes instead of installing it.
add_library(shadowhook_calibration SHARED shadowhook/nothing/sh_nothing.c)
set_target_properties(shadowhook_calibration PROPERTIES OUTPUT_NAME shadowhook_nothing)
target_compile_options(shadowhook_calibration PRIVATE
        -Oz -fno-ident -fno-unwind-tables -fno-asynchronous-unwind-tables)
target_link_options(shadowhook_calibration PRIVATE
        -Wl,--strip-all -Wl,--as-needed -Wl,--hash-style=sysv -Wl,--build-id=none
        -Wl,-nostdlib -nostartfiles -nodefaultlibs)

set(SHADOWHOOK_CALIBRATION_ASSEMBLY "${CMAKE_CURRENT_BINARY_DIR}/shadowhook_calibration.S")
file(GENERATE OUTPUT "${SHADOWHOOK_CALIBRATION_ASSEMBLY}" CONTENT
".section .rodata.signature_shadowhook_calibration,\"a\"
.balign 16
.global signature_shadowhook_calibration_start
.hidden signature_shadowhook_calibration_start
signature_shadowhook_calibration_start:
.incbin \"$<TARGET_FILE:shadowhook_calibration>\"
.global signature_shadowhook_calibration_end
.hidden signature_shadowhook_calibration_end
signature_shadowhook_calibration_end:
.section .note.GNU-stack,\"\",%progbits
")
set_source_files_properties("${SHADOWHOOK_CALIBRATION_ASSEMBLY}" PROPERTIES
        OBJECT_DEPENDS "${CMAKE_CURRENT_BINARY_DIR}/libshadowhook_nothing.so")
target_sources(shadowhook PRIVATE "${SHADOWHOOK_CALIBRATION_ASSEMBLY}")
add_dependencies(shadowhook shadowhook_calibration)
