cmake_minimum_required(VERSION 3.22)

project(SignatureKiller C)

set(CMAKE_C_FLAGS "-Wall -Wextra -Werror -fvisibility=hidden")

add_library(
        SignatureKiller
        SHARED
        mt_jni.c
        xhook.c
        xh_core.c
        xh_elf.c
        xh_log.c
        xh_util.c
        xh_version.c)

find_library(
        log-lib
        log)

target_link_libraries(
        SignatureKiller
        ${log-lib})
