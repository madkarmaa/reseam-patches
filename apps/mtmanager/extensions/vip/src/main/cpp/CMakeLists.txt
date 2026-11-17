# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-License-Identifier: AGPL-3.0-or-later

cmake_minimum_required(VERSION 3.22)
project(mtvip LANGUAGES CXX)

add_library(mtvip SHARED bridge.cpp)
target_compile_features(mtvip PRIVATE cxx_std_23)
target_compile_options(mtvip PRIVATE
        -Wall -Wextra -Werror -fno-exceptions -fno-rtti -fvisibility=hidden)
target_link_options(mtvip PRIVATE
        -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--strip-debug
        -Wl,-z,max-page-size=16384)
install(TARGETS mtvip LIBRARY DESTINATION ${ANDROID_ABI})
