// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "license_check.h"

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    bool installed = false;

    bool accept_verdict(const license_check &check) {
        const long page_size = sysconf(_SC_PAGESIZE);
        if (page_size <= 0) return false;

        const auto address = reinterpret_cast<uintptr_t>(check.instruction);
        const auto page_address = address & ~static_cast<uintptr_t>(page_size - 1);
        auto *page = reinterpret_cast<void *>(page_address);
        const auto writable_size = address - page_address + sizeof(uint32_t);

        if (mprotect(page, writable_size, check.protection | PROT_WRITE) != 0) {
            LOG_ERROR("Cannot write license-check code: %s", strerror(errno));
            return false;
        }

        // Installed while the native license VM initializes, before any playback pipeline exists.
        const auto accepted = (*check.instruction & license_check_keep) | license_check_replacement;
        __atomic_store_n(check.instruction, accepted, __ATOMIC_RELEASE);
        auto *code = reinterpret_cast<char *>(check.instruction);
        __builtin___clear_cache(code, code + sizeof(uint32_t));

        if (mprotect(page, writable_size, check.protection) != 0) {
            LOG_ERROR("Cannot restore license-check code protection: %s", strerror(errno));
            return false;
        }

        return true;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_extensions_poweramp_Playback_allowPlaybackNative(JNIEnv *, jclass) {
    if (installed) return JNI_TRUE;

    const auto checks = find_license_checks();
    if (checks.size() != expected_license_checks) {
        LOG_ERROR("Expected %u pipeline license checks, found %zu", expected_license_checks, checks.size());
        return JNI_FALSE;
    }

    for (const auto &check : checks) {
        if (!accept_verdict(check)) return JNI_FALSE;
        LOG_INFO("Pipeline license check at %p accepts every verdict", static_cast<void *>(check.instruction));
    }

    installed = true;
    return JNI_TRUE;
}
