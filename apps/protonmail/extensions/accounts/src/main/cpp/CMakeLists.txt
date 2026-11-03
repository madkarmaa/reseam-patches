# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)
project(ProtonAccounts CXX ASM)

if (NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "ProtonAccounts currently supports arm64-v8a")
endif ()

add_library(ProtonAccounts SHARED account_limit.S account_limit.cpp accounts.cpp)
target_compile_features(ProtonAccounts PRIVATE cxx_std_23)
target_compile_options(ProtonAccounts PRIVATE
        "$<$<COMPILE_LANGUAGE:CXX>:-Wall;-Wextra;-Werror;-fno-exceptions;-fno-rtti;-fvisibility=hidden>")
target_link_options(ProtonAccounts PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-debug)
target_link_libraries(ProtonAccounts PRIVATE log dl)
install(TARGETS ProtonAccounts LIBRARY DESTINATION ${ANDROID_ABI})
